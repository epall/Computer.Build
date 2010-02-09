require 'computer_build/vhdl'

module ComputerBuild
  class State
    include VHDL::StatementBlock

    attr_reader :name

    def initialize(name, body)
      @name = name
      @statements = []
      body[self]
    end

    def self.full_name(shortname)
      ("state_"+shortname.to_s).to_sym
    end
  end

  class Transition
    attr_reader :from, :to, :condition
    def initialize(options)
      @from = options[:from]
      @to = options[:to]
      @condition = options[:condition] || options[:on]
    end
  end

  class StateMachine
    def initialize(name, body)
      @name = name
      @inputs = {}
      @outputs = {}
      @signals = []
      body[self]
    end

    def inputs(*rest)
      @inputs = rest
    end

    def input(name, type)
      @inputs[name] = type
    end

    def outputs(*rest)
      @outputs = rest
    end

    def output(name, type)
      @outputs[name] = type
    end

    def signal(*rest)
      @signals << rest
    end

    def state(name, &body)
      @states ||= []
      @states << State.new(name, body)
    end

    def reset(&body)
      @reset = body
    end

    def transition(options)
      @transitions ||= []
      @transitions << Transition.new(options)
    end

    def generate
      representation = entity(@name) do |e|
        e.port "clock", :in, "std_logic"

        @inputs.each do |name, type|
          e.port name, :in, type
        end

        @outputs.each do |name, type|
          e.port name, :out, type
        end

        e.type "STATE_TYPE", @states.map {|s| "state_"+s.name.to_s}
        e.signal "state", "STATE_TYPE"
        @signals.each do |args|
          e.signal(*args)
        end

        e.behavior do |b|
          b.process [:clock] do |p|
            if @reset
              ifthenelse = p.if(equal(:reset, '1')) do |b|
                def b.goto(state)
                  self.assign(:state, ("state_"+state.to_s).to_sym)
                end
                @reset[b]
              end

              ifthenelse.elsif(event(:clock), equal(:clock, "1")) do |b|
                b.case :state do |c|
                  @states.each do |state|
                    c["state_" + state.name.to_s] = state
                  end
                end
              end
            else
              p.if(event(:clock), equal(:clock, "1")) do |b|
                b.case :state do |c|
                  @states.each do |state|
                    c["state_" + state.name.to_s] = state
                  end
                end
              end
            end

            @transitions.each do |transition|
              conditions = [equal(:state, State.full_name(transition.from))]
              conditions << transition.condition unless transition.condition.nil?
              p.if(*conditions) do |b|
                b.assign(:state, State.full_name(transition.to))
              end
            end
          end # process
        end
      end

      representation.generate
    end
  end
end

def state_machine(name, &body)
  ComputerBuild::StateMachine.new(name, body)
end
