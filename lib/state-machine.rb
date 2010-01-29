require 'computer_build/vhdl'

class State
  attr_reader :name
  def initialize(name, body)
    @name = name
    body[self]
  end

  def assign(target, value)
    @assigns ||= []
    @assigns << [target, value]
  end
end

class Transition
  def initialize(options)
    @from = options[:from]
    @to = options[:to]
    @condition = options[:condition]
  end
end

class StateMachine
  def initialize(name, body)
    @name = name
    body[self]
  end

  def inputs(*rest)
    @inputs = rest
  end

  def outputs(*rest)
    @outputs = rest
  end

  def state(name, &body)
    @states ||= []
    @states << State.new(name, body)
  end

  def transition(options)
    @transitions ||= []
    @transitions << Transition.new(options)
  end

  def generate
    representation = entity(@name) do |e|
      e.port "clock", :in, "std_logic"

      @inputs.each do |name|
        e.port name, :in, "std_logic"
      end

      @outputs.each do |name|
        e.port name, :out, "std_logic"
      end

      e.type "STATE_TYPE", @states.map {|s| "state_"+s.name.to_s}
      e.signal "state", "STATE_TYPE"

      e.behavior do |b|
        b.process @inputs + ["clock"] do |p|
          p.if(event(:clock), equal(:clock, "1")) do |b|
            b.case :state do |c|
              c["state_on"] = assign :bulb, "1"
            end
          end
        end
      end
    end

    representation.generate
  end
end

def state_machine(name, &body)
  StateMachine.new(name, body)
end

pushbutton = state_machine "pushbutton" do |m|
  m.inputs :push
  m.outputs :bulb

  m.state :on do |s|
    s.assign :bulb, "1"
  end

  m.state :off do |s|
    s.assign :bulb, "0"
  end

  m.transition :from => :on, :to => :off, :condition => equal(:push, "1")
  m.transition :from => :off, :to => :on, :condition => equal(:push, "0")
end

generate_vhdl pushbutton
