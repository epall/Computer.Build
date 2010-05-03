require 'computer_build/vhdl'
require 'computer_build/state_machine'

class Computer
  include VHDL::Helpers

  def self.Build(name)
    instance = Computer.new(name)
    yield(instance)
    instance.generate
  end
  
  attr_writer :address_width
  
  def initialize(name)
    @name = name
    @instructions = []
  end
  
  # DSL method
  def instruction(name)
    inst = Instruction.new(name)
    yield(inst)
    @instructions << inst
  end
  
  def generate
    states = make_states(@instructions).merge(static_states)
    opcodes = make_opcodes(@instructions)
    opcode_length = opcodes.values.first.length
    control_signals = states.values.map(&:control_signals).flatten.uniq

    control = state_machine("control_unit") do |m|
      m.input :reset, VHDL::STD_LOGIC
      m.input :condition, VHDL::STD_LOGIC
      m.inout :system_bus, VHDL::STD_LOGIC_VECTOR(7..0)
      m.output :alu_operation, VHDL::STD_LOGIC_VECTOR(2..0)
      control_signals.each do |sig|
        m.output sig, VHDL::STD_LOGIC
      end

      m.signal :opcode,
        VHDL::STD_LOGIC_VECTOR((opcode_length-1)..0)

      states.values.map(&:constant_value).compact.uniq.each do |const|
        m.constant "CONSTANT_#{const}", VHDL::STD_LOGIC_VECTOR(7..0), const.to_logic(8)
      end

      m.reset do |r|
        r.goto :fetch
        control_signals.each do |sig|
          r.low sig.to_sym
        end
        r.assign :alu_operation, "000"
        r.assign :system_bus, "ZZZZZZZZ"
      end

      states.each do |name, state|
        m.state(name) do |s|
          control_signals.each do |sig|
            s.assign sig, state.control_signals.include?(sig) ? '1' : '0'
          end
          s.assign :alu_operation, state.alu_op ? state.alu_op.opcode : "000"

          if state.constant_value
            s.assign :system_bus, "CONSTANT_#{state.constant_value}".to_sym
          else
            s.assign :system_bus, "ZZZZZZZZ"
          end

          if name == 'store_instruction'
            s.if event(:clock), VHDL::Equal.new(:clock,"0") do |thn|
              thn.assign :opcode, "#{opcode_length-1} downto 0",
                :system_bus, "7 downto #{7-opcode_length+1}"
            end
          end
        end

        if state.condition
          m.transition :from => name, :to => state.next,
            :on => VHDL::Equal.new(:condition, "0")
          m.transition :from => name, :to => name+"_0",
            :on => VHDL::Equal.new(:condition, "1")
        else
          m.transition :from => name, :to => state.next if state.next
        end
      end

      # instruction decode
      opcodes.each do |instruction, opcode|
        m.transition :from => :decode, :to => instruction.name+"_0",
          :on => equal(:opcode, opcode)
      end
    end

    main = entity("main") do |e|
      e.port :clock ,:in, VHDL::STD_LOGIC
      e.port :reset, :in, VHDL::STD_LOGIC
      e.port :bus_inspection, :out, VHDL::STD_LOGIC_VECTOR(7..0)

      e.signal :system_bus, VHDL::STD_LOGIC_VECTOR(7..0)
      e.signal :alu_operation, VHDL::STD_LOGIC_VECTOR(2..0)
      e.signal :alu_condition, VHDL::STD_LOGIC

      control_signals.each do |sig|
        e.signal sig, VHDL::STD_LOGIC
      end

      e.component :reg do |c|
        c.in :clock, VHDL::STD_LOGIC
        c.in :data_in, VHDL::STD_LOGIC_VECTOR(7..0)
        c.out :data_out, VHDL::STD_LOGIC_VECTOR(7..0)
        c.in :wr, VHDL::STD_LOGIC
        c.in :rd, VHDL::STD_LOGIC
      end

      e.component :program_counter do |c|
        c.in :clock, VHDL::STD_LOGIC
        c.in :data_in, VHDL::STD_LOGIC_VECTOR(7..0)
        c.out :data_out, VHDL::STD_LOGIC_VECTOR(7..0)
        c.in :wr, VHDL::STD_LOGIC
        c.in :rd, VHDL::STD_LOGIC
        c.in :inc, VHDL::STD_LOGIC
      end

      e.component :ram do |c|
        c.in :clock, VHDL::STD_LOGIC
        c.in :data_in, VHDL::STD_LOGIC_VECTOR(7..0)
        c.out :data_out, VHDL::STD_LOGIC_VECTOR(7..0)
        c.in :address, VHDL::STD_LOGIC_VECTOR(4..0)
        c.in :wr_data, VHDL::STD_LOGIC
        c.in :wr_addr, VHDL::STD_LOGIC
        c.in :rd, VHDL::STD_LOGIC
      end

      e.component :alu do |c|
        c.in :clock, VHDL::STD_LOGIC
        c.in :data_in, VHDL::STD_LOGIC_VECTOR(7..0)
        c.out :data_out, VHDL::STD_LOGIC_VECTOR(7..0)
        c.in :op, VHDL::STD_LOGIC_VECTOR(2..0)
        c.in :wr_a, VHDL::STD_LOGIC
        c.in :wr_b, VHDL::STD_LOGIC
        c.in :rd, VHDL::STD_LOGIC
        c.out :condition, VHDL::STD_LOGIC
      end

      e.component :control_unit do |c|
        c.in :clock, VHDL::STD_LOGIC
        c.in :reset, VHDL::STD_LOGIC
        c.in :condition, VHDL::STD_LOGIC
        c.out :alu_operation, VHDL::STD_LOGIC_VECTOR(2..0)
        control_signals.each do |sig|
          c.out sig, VHDL::STD_LOGIC
        end
        c.inout :system_bus, VHDL::STD_LOGIC_VECTOR(7..0)
      end

      e.behavior do |b|
        b.instance :program_counter, "pc", :clock, :system_bus, :system_bus,
          :wr_pc, :rd_pc, :inc_pc
        b.instance :reg, "ir", :clock, :system_bus, :system_bus, :wr_IR, :rd_IR
        b.instance :reg, "A", :clock, :system_bus, :system_bus, :wr_A, :rd_A
        b.instance :ram, "main_memory", :clock, :system_bus, :system_bus,
          subbits(:system_bus, 4..0), :wr_MD, :wr_MA, :rd_MD
        b.instance :alu, "alu0", :clock, :system_bus, :system_bus,
          :alu_operation, :wr_alu_a, :wr_alu_b, :rd_alu, :alu_condition
        b.instance :control_unit, "control0", [:clock, :reset, :alu_condition,
            :alu_operation] + control_signals + [:system_bus]
        b.assign :bus_inspection, :system_bus
      end
    end
    
    Dir.mkdir @name rescue nil # ignore error if dir already exists
    File.open(File.join(@name, 'control.vhdl'), 'w') do |f|
      generate_vhdl(control, f)
    end

    File.open(File.join(@name, 'main.vhdl'), 'w') do |f|
      generate_vhdl(main, f)
    end
  end
  
  # inner classes
  
  class Instruction
    attr_reader :name, :steps

    def initialize(name)
      @name = name
      @steps = []
    end
    
    def move(target, source)
      @steps << RTL.new(target, source)
    end

    def microcode
      steps.map(&:to_microcode).flatten
    end

    def if(condition, &body)
      @steps << Conditional.new(condition, &body)
    end
  end
  
  class ALUOperation
    attr_reader :op, :operands

    def initialize(op, *operands)
      @op = op
      @operands = operands
    end

    def opcode
      return {
        :and        => "001",
        :or         => "010",
        :complement => "011",
        :add        => "100",
        :subtract   => "101",
        :equal      => "110",
        :lessthan   => "111"}[@op]
    end
  end

  private

  class Conditional
    def initialize(condition)
      @condition = condition # instance of ALUOperation
      @steps = []
      @true_body = []
      yield self
    end

    # DSL method
    def move(target, source)
      @steps << RTL.new(target, source)
    end

    def to_microcode
      steps = []
      steps << MicrocodeState.new do |state|
        state.control_signals = ["rd_#{@condition.operands.first}", "wr_alu_a"]
        state.alu_op = @condition
      end
      conditional = MicrocodeState.new do |state|
        op = @condition.operands.last
        state.control_signals = ["wr_alu_b"]

        if op.is_a? Fixnum
          state.constant_value = op
        else
          state.control_signals += "rd_#{op}"
        end

        state.alu_op = @condition
        state.condition = @condition
      end
      steps << conditional

      body = @steps.map(&:to_microcode).flatten
      conditional.body_size = body.length

      body.each {|state| state.conditional = conditional}

      return steps + body
    end
  end

  class RTL
    def initialize(target, source)
      @target = target
      @source = source
    end
    
    def to_microcode
      if @source.is_a? Fixnum
        return MicrocodeState.new do |state|
          state.control_signals = "wr_#{@target}"
          state.constant_value = @source
        end
      elsif @source.is_a? Symbol
        return MicrocodeState.new do |state|
          state.control_signals = ["wr_#{@target}", "rd_#{@source}"]
        end
      elsif @source.is_a? ALUOperation
        steps = []
        steps << MicrocodeState.new do |state|
          state.control_signals = ["rd_#{@source.operands.first}", "wr_alu_a"]
          state.alu_op = @source
        end

        if @source.operands.length == 2
          steps << MicrocodeState.new do |state|
            state.control_signals = ["wr_alu_b"]
            if @source.operands.last.is_a? Fixnum
              state.constant_value = @source.operands.last
            else
              state.control_signals << "rd_#{@source.operands.last}"
            end
          end
        end

        steps << MicrocodeState.new do |state|
          state.control_signals = ["rd_alu", "wr_#{@target}"]
          state.alu_op = @source
        end
        return steps
      end
    end
  end

  class MicrocodeState
    attr_accessor :control_signals, :alu_op, :constant_value, :next,
      :condition, :conditional, :body_size, :index

    def initialize(&blk)
      yield(self) if blk
    end
  end

  def make_states(instructions)
    states = {}
    instructions.each do |instr|
      steps = instr.microcode
      indexes = {nil => 0}
      steps.each do |step|
        indexes[step.conditional] ||= 0
        index = indexes[step.conditional]

        if step.conditional
          if index < step.conditional.body_size - 1
            step.next = instr.name+"_"+(step.conditional.index.to_s)+"_"+(index+1).to_s
          else
            step.next = step.conditional.next
          end
          states[instr.name+"_"+(step.conditional.index.to_s)+"_"+index.to_s] = step
        else
          if index < steps.reject(&:conditional).length - 1
            step.next = instr.name+"_"+(index+1).to_s
          else
            step.next = :fetch
          end
          states[instr.name+"_"+index.to_s] = step
        end
        step.index = index
        indexes[step.conditional] += 1
      end
    end

    return states
  end

  def make_opcodes(instructions)
    bits = (Math.log(instructions.length)/Math.log(2)).ceil
    opcodes = {}
    instructions.each_with_index do |instruction, idx|
      bin_string = idx.to_s(2)
      bin_string = ("0" * (bits - bin_string.length)) + bin_string
      opcodes[instruction] = bin_string
    end
    return opcodes
  end

  def static_states
    states = {}
    states['fetch'] = MicrocodeState.new do |s|
      s.control_signals = ['rd_pc', 'wr_MA']
      s.next = 'store_instruction'
    end

    states['store_instruction'] = MicrocodeState.new do |s|
      s.control_signals = ['rd_MD', 'wr_IR', 'inc_pc']
      s.next = 'decode'
    end

    states['decode'] = MicrocodeState.new do |s|
      s.control_signals = []
    end

    return states
  end
end

def complement(value)
  Computer::ALUOperation.new(:complement, value)
end

def add(operand1, operand2)
  Computer::ALUOperation.new(:add, operand1, operand2)
end

def bitwise_and(operand1, operand2)
  Computer::ALUOperation.new(:and, operand1, operand2)
end

def subtract(operand1, operand2)
  Computer::ALUOperation.new(:subtract, operand1, operand2)
end

def equal(operand1, operand2)
  Computer::ALUOperation.new(:equal, operand1, operand2)
end
