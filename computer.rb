# When run through Computer.Build, this should produce valid VHDL
# that implements the specified computer.
Computer.Build do |c|
  c.instruction "add" do |x, y|
    memory[y] = memory[x]+memory[y]
  end

  c.instruction "subtract" do |x, y|
    memory[y] = memory[x]-memory[y]
  end

  c.instruction "move" do |addr1, addr2|
    memory[addr2] = memory[addr1]
  end

  c.instruction "jump" do |addr|
    next_address = addr
  end

  c.instruction "jeq", "Jump if equal" do |x, y, addr|
    if memory[x] == memory[y]
      next_address = addr
    end
  end
end
