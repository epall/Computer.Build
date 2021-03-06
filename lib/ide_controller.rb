require 'Computer.Build'


controller = state_machine "ide_controller" do |m|
  m.input  :reset, VHDL::STD_LOGIC
  m.input  :instr_command, VHDL::STD_LOGIC_VECTOR(7..0)
  m.input  :instr_address, VHDL::STD_LOGIC_VECTOR(7..0)
  m.input  :instr_data, VHDL::STD_LOGIC_VECTOR(15..0)
  m.output :result_command, VHDL::STD_LOGIC_VECTOR(7..0)
  m.output :result_status, VHDL::STD_LOGIC_VECTOR(7..0)
  m.output :result_data, VHDL::STD_LOGIC_VECTOR(15..0)
  m.input  :available_instr, VHDL::STD_LOGIC
  m.output :read_instr, VHDL::STD_LOGIC
  m.output :write_result, VHDL::STD_LOGIC

  m.output :ide_reset_n, VHDL::STD_LOGIC
  m.input  :ide_data_in, VHDL::STD_LOGIC_VECTOR(15..0)
  m.output :ide_data_out, VHDL::STD_LOGIC_VECTOR(15..0)
  m.output :ide_data_write_n, VHDL::STD_LOGIC
  m.output :ide_data_read_n, VHDL::STD_LOGIC
  m.output :ide_address, VHDL::STD_LOGIC_VECTOR(2..0)
  m.output :ide_cs_1f0_n, VHDL::STD_LOGIC
  m.output :ide_cs_3f0_n, VHDL::STD_LOGIC
  m.input  :ide_ready, VHDL::STD_LOGIC
  m.input  :ide_int_request, VHDL::STD_LOGIC
  m.input  :ide_16_bit_n, VHDL::STD_LOGIC
  m.input  :ide_dasp_n, VHDL::STD_LOGIC
  m.output :ide_data_OE, VHDL::STD_LOGIC

  m.signal :command_buffer, VHDL::STD_LOGIC_VECTOR(7..0)
  m.signal :address_buffer, VHDL::STD_LOGIC_VECTOR(7..0)
  m.signal :data_buffer, VHDL::STD_LOGIC_VECTOR(15..0)

  m.reset do |r|
    r.goto :wait

    r.low :ide_reset_n
    r.high :ide_data_write_n
    r.high :ide_data_read_n
    r.assign :ide_address, '000'
    r.high :ide_cs_1f0_n
    r.high :ide_cs_3f0_n
    r.assign :ide_data_out, '0000000000000000'

    r.assign :result_command, "00000000"
    r.assign :result_status, "00000000"
    r.assign :result_data, "0000000000000000"
    r.low :read_instr
    r.low :write_result
  end

  m.state :wait do |s|
    s.high :ide_reset_n
    s.low :write_result
    s.assign :read_instr, :available_instr
  end

  m.transition :from => :wait, :to => :decode, :on => equal(:available_instr, '1')

  m.state :decode do |s|
    s.assign :command_buffer, :instr_command
    s.assign :address_buffer, :instr_address
    s.assign :data_buffer, :instr_data
    s.low :read_instr

    s.case(:instr_command) do |c|
      c["00000000"] = block do |b|
        b.assign :result_command, :instr_command
        b.assign :result_status, "00000000"
        b.assign :result_data, "0000000000000000"
      end

      c["00000011"] = block do |b|
        b.assign :result_command, :instr_command
        b.assign :ide_address, 0, :instr_address, 0
        b.assign :ide_address, 1, :instr_address, 1
        b.assign :ide_address, 2, :instr_address, 2
      end

      c["00000010"] = block do |b|
        b.assign :result_command, :instr_command
        b.assign :ide_address, 0, :instr_address, 0
        b.assign :ide_address, 1, :instr_address, 1
        b.assign :ide_address, 2, :instr_address, 2
        b.low :ide_cs_1f0_n
      end
    end
  end

  m.transition :from => :decode, :to => :writestatus,
    :on => equal(:instr_command, "00000000")
  m.transition :from => :decode, :to => :data_on_bus,
    :on => equal(:instr_command, "00000011")
  m.transition :from => :decode, :to => :data_on_bus,
    :on => equal(:instr_command, "00000010")
  
  m.state :writestatus do |s|
    s.high :write_result
    s.high :ide_cs_1f0_n
    s.high :ide_data_read_n
  end

  m.transition :from => :writestatus, :to => :wait

  m.state :action do |s|
    ifelse = s.if equal(:instr_command, "00000011") do |b|
      b.assign :result_data, :ide_data_in
    end

    ifelse.else do |b|
      b.assign :result_data, "0000000000000000"
    end
    s.low :ide_data_OE
  end

  m.transition :from => :action, :to => :writestatus

  m.state :data_on_bus do |s|
    s.if equal(:command_buffer, "00000011") do |b|
      b.high :ide_data_write_n
      b.low :ide_data_read_n
    end
  end

  m.transition :from => :data_on_bus, :to => :action,
    :condition => equal(:command_buffer, "00000011")
end

generate_vhdl controller
