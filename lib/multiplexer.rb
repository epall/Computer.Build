require 'computer_build/vhdl'

mux = entity "Mux" do |mux|
  mux.port :I3, :in, "std_logic_vector(2 downto 0)"
  mux.port :I2, :in, "std_logic_vector(2 downto 0)"
  mux.port :I1, :in, "std_logic_vector(2 downto 0)"
  mux.port :I0, :in, "std_logic_vector(2 downto 0)"
  mux.port :S, :in, "std_logic_vector(1 downto 0)"
  mux.port :O, :out, "std_logic_vector(2 downto 0)"
  mux.behavior do |b|
    b.process [:I3, :I2, :I1, :I0, :S] do |p|
      p.case :S do |c|
        c["00"] = :O <= :I0
        c["01"] = :O <= :I1
        c["10"] = :O <= :I2
        c["11"] = :O <= :I3
        c["others"] = :O <= "ZZZ"
      end
    end
  end
end

generate_vhdl mux
