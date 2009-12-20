module VHDL
  class Entity
    attr_reader :name
    def initialize(name, body)
      @name = name
      body[self]
    end

    def port(id, direction, description)
      @ports ||= []
      @ports << Port.new(id, direction, description)
    end

    def behavior(&body)
      @behavior = Behavior.new(body)
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
      puts "begin"
      @behavior.generate 1
      puts "end arch_#{@name};"
    end
  end

  class Port
    def initialize(id, direction, description)
      @id = id
      @direction = direction
      @description = description
    end
    
    def generate(indent, last)
      print "  " * indent
      print "#{@id}: #{@direction} #{@description}"
      if last
        puts
      else
        puts ';'
      end
    end
  end

  class Behavior
    def initialize(body)
      body.call(self)
    end

    def process(inputs, &body)
      @process = VHDL::Process.new(inputs, body)
    end
    
    def generate(indent)
      @process.generate(indent+1)
    end
  end

  class Process
    def initialize(inputs, body)
      @inputs = inputs
      body.call(self)
    end

    def case(input, &body)
      @case = Case.new(input, body)
    end

    def generate(indent)
      prefix = "  " * indent
      args = @inputs.map(&:to_s).join(',')
      puts prefix + "process(#{args})"
      puts prefix + "begin"
      @case.generate(indent + 2)
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

  class Assign
    def initialize(target, expression)
      @target = target
      @expression = expression
    end
    
    def generate
      expression = @expression
      expression = "\"#{expression}\"" if expression.instance_of? String
      return "#{@target} <= #{expression}"
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

def generate_vhdl(entity)
  puts "library ieee;"
  puts "use ieee.std_logic_1164.all;"
  entity.generate
end
