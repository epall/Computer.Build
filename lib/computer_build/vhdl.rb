module VHDL
  module StatementBlock
    def case(input, &body)
      @statements << Case.new(input, body)
    end

    def if(*conditions, &body)
      @statements << If.new(conditions, body)
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
      @statements.each {|s| s.generate(indent + 2)}
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
      @conditions.sort.each do |pair|
        condition, expression = pair
        puts prefix+"  "+"when \"#{condition}\" => #{expression.generate};"
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
