module VHDL
  STD_LOGIC = "std_logic"

  def self.STD_LOGIC_VECTOR(range)
    if range.first > range.last
      return "std_logic_vector(#{range.first} downto #{range.last})"
    else
      return "std_logic_vector(#{range.first} upto #{range.last})"
    end
  end

  module StatementBlock
    def case(input, &body)
      @statements << Case.new(input, body)
    end

    def if(*conditions, &body)
      @statements << If.new(conditions, body)
    end

    def assign(target, expression)
      @statements << Assignment.new(target, expression)
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
      puts "entity #{@name} is"
      puts "port("
      @ports.each_with_index do |port, index|
        port.generate 1, (index == @ports.length-1)
      end
      puts ");"
      puts "end #{@name};"
      puts "architecture arch_#{@name} of #{@name} is"
      @types.each {|t| t.generate 1}
      @signals.each {|t| t.generate 1}
      puts "begin"
      @behavior.generate 1
      puts "end arch_#{@name};"
    end
  end

  class Type < SingleLineStatement
    def initialize(name, values)
      @name = name
      @values = values
    end

    def line
      "type #{@name} is ( #{@values.join(", ")} );"
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
      "signal #{@id} : #{@type};"
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
      puts prefix + "process(#{args})"
      puts prefix + "begin"
      @statements.each {|s| s.generate(indent + 1)}
      puts prefix + "end process;"
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
      puts prefix+"case #{@input} is"
      @conditions.each do |pair|
        condition, expression = pair
        print prefix+"  when "
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
      puts prefix+"end case;"
    end
  end

  class If < MultiLineStatement
    include StatementBlock

    def initialize(conditions, body)
      @conditions = conditions
      @statements = []
      body[self]
    end

    def generate(indent)
      conditions = @conditions.map(&:generate).join(' and ')
      puts ("  "*indent)+"if #{conditions} then"
      @statements.each {|s| s.generate(indent+2)}
      puts ("  "*indent)+"end if;"
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
    def initialize(target, expression)
      @target = target
      @expression = expression
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

def generate_vhdl(entity)
  puts "library ieee;"
  puts "use ieee.std_logic_1164.all;"
  entity.generate
end
