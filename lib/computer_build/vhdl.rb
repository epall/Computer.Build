module VHDL
  STD_LOGIC = "STD_LOGIC"

  def self.STD_LOGIC_VECTOR(range)
    if range.first > range.last
      return "STD_LOGIC_VECTOR(#{range.first} downto #{range.last})"
    else
      return "STD_LOGIC_VECTOR(#{range.first} upto #{range.last})"
    end
  end

  module StatementBlock
    def case(input, &body)
      @statements << Case.new(input, body)
    end

    def if(*conditions, &body)
      ifthenelse = If.new(conditions, body)
      @statements << ifthenelse
      return ifthenelse
    end

    def assign(*args)
      @statements << Assignment.new(*args)
    end

    # Default generate, generally overridden
    def generate(indent)
      @statements.each {|s| s.generate(indent + 1)}
    end
  end

  class SingleLineStatement
    def generate(indent)
      print "  " * indent
      print self.line()
      print "\n"
    end
  end
  
  class MultiLineStatement
  end

  class InlineStatement
    protected

    def quoted(expression)
      if expression.instance_of? String
        if expression.length == 1
          return "'#{expression}'"
        else
          return "\"#{expression}\""
        end
      else
        return expression
      end
    end
  end

  class Entity
    attr_reader :name
    def initialize(name, body)
      @name = name
      @ports = []
      @signals = []
      @types = []
      body[self]
    end

    def port(*args)
      @ports << Port.new(*args)
    end

    def signal(*args)
      @signals << Signal.new(*args)
    end


    def behavior(&body)
      @behavior = Behavior.new(body)
    end

    def type(*args)
      @types << Type.new(*args)
    end

    def generate
      puts "ENTITY #{@name} IS"
      puts "PORT("
      @ports.each_with_index do |port, index|
        port.generate 1, (index == @ports.length-1)
      end
      puts ");"
      puts "END #{@name};"
      puts "ARCHITECTURE arch_#{@name} OF #{@name} IS"
      @types.each {|t| t.generate 1}
      @signals.each {|t| t.generate 1}
      puts "BEGIN"
      @behavior.generate 1
      puts "END arch_#{@name};"
    end
  end

  class Type < SingleLineStatement
    def initialize(name, values)
      @name = name
      @values = values
    end

    def line
      "TYPE #{@name} IS ( #{@values.join(", ")} );"
    end
  end

  class Port < SingleLineStatement
    def initialize(id, direction, description)
      @id = id
      @direction = direction
      @description = description
    end
    
    def generate(indent, last)
      print "  " * indent
      print "#{@id}: #{@direction} #{@description}"
      puts last ? '' : ';'
    end
  end

  class Signal < SingleLineStatement
    def initialize(id, type)
      @id = id
      @type = type
    end

    def line
      "SIGNAL #{@id} : #{@type};"
    end
  end

  class Behavior
    def initialize(body)
      @definition = []
      body.call(self)
    end

    def process(inputs, &body)
      @definition << VHDL::Process.new(inputs, body)
    end
    
    def generate(indent)
      @definition.each {|d| d.generate(indent+1) }
    end
  end

  class Process
    include StatementBlock
    def initialize(inputs, body)
      @inputs = inputs
      @statements = []
      body[self]
    end

    def generate(indent)
      prefix = "  " * indent
      args = @inputs.map(&:to_s).join(',')
      puts prefix + "PROCESS(#{args})"
      puts prefix + "BEGIN"
      @statements.each {|s| s.generate(indent + 1)}
      puts prefix + "END PROCESS;"
    end
  end

  class Case
    def initialize(input, body)
      @input = input
      @conditions = {}
      body.call(@conditions)
    end

    def generate(indent)
      prefix = "  " * indent
      puts prefix+"CASE #{@input} IS"
      @conditions.each do |pair|
        condition, expression = pair
        print prefix+"  WHEN "
        if condition =~ /^\d$/
          print "'#{condition}'"
        elsif condition =~ /^\d+$/
          print "\"#{condition}\""
        else
          print condition
        end
        print " =>"
        if expression.is_a? InlineStatement
          puts expression.generate
        else
          puts
          expression.generate(indent+1)
        end
      end
      puts prefix+"END CASE;"
    end
  end

  class If < MultiLineStatement
    include StatementBlock

    def initialize(conditions, body)
      @conditions = conditions
      @compound = false
      @statements = []
      body[self]
    end

    def elsif(*conditions, &body)
      unless @compound
        @clauses = [@statements]
        @conditions = [@conditions]
      end
      @compound = true

      @statements = []
      body.call(self)
      @clauses << @statements
      @conditions << conditions
    end

    def else(*conditions, &body)
      @whentrue = @statements
      @statements = []
      body.call(self)
    end

    def generate(indent)
      if @compound
        conditions = @conditions.first.map(&:generate).join(' and ')
        puts ("  "*indent)+"IF #{conditions} THEN"
        @clauses.first.each {|s| s.generate(indent+1)}
        @clauses[1..100].zip(@conditions[1..100]).each do |statements, conditions|
          conditions = conditions.map(&:generate).join(' and ')
          puts ("  "*indent)+"ELSIF #{conditions} THEN"
          statements.each {|s| s.generate(indent+1)}
        end
        puts ("  "*indent)+"END IF;"
      elsif @whentrue
        conditions = @conditions.map(&:generate).join(' and ')
        puts ("  "*indent)+"IF #{conditions} THEN"
        @whentrue.each {|s| s.generate(indent+1)}
        puts ("  "*indent)+"ELSE"
        @statements.each {|s| s.generate(indent+1)}
        puts ("  "*indent)+"END IF;"
      else
        conditions = @conditions.map(&:generate).join(' and ')
        puts ("  "*indent)+"IF #{conditions} THEN"
        @statements.each {|s| s.generate(indent+1)}
        puts ("  "*indent)+"END IF;"
      end
    end
  end

  class Assignment < SingleLineStatement
    def initialize(*args)
      @assign = Assign.new(*args)
    end

    def line
      @assign.generate + ";"
    end
  end

  class Assign < InlineStatement
    def initialize(*args)
      if args.length == 2
        @target = args[0]
        @expression = args[1]
      else
        @target = args[0].to_s + "(#{args[1]})"
        @expression = args[2].to_s + "(#{args[3]})"
        @expression = @expression.to_sym
      end
    end
    
    def generate
      "#{@target} <= #{quoted(@expression)}"
    end
  end

  class Equal < InlineStatement
    def initialize(target, expression)
      @target = target
      @expression = expression
    end
    
    def generate
      "#{@target} = #{quoted(@expression)}"
    end
  end

  class Event < InlineStatement
    def initialize(target)
      @target = target
    end

    def generate
      "#{@target.to_s}'EVENT"
    end
  end

  class Block < MultiLineStatement
    include StatementBlock

    def initialize(body)
      @statements = []
      body.call(self)
    end
  end
end

# Global scope methods for creating stuff

def entity(name, &body)
  VHDL::Entity.new(name, body)
end

def assign(target, expression)
  VHDL::Assign.new(target, expression)
end

def equal(target, expression)
  VHDL::Equal.new(target, expression)
end

def event(target)
  VHDL::Event.new(target)
end

def block(&body)
  VHDL::Block.new(body)
end

def generate_vhdl(entity)
  puts "LIBRARY ieee;"
  puts "USE ieee.std_logic_1164.all;"
  entity.generate
end
