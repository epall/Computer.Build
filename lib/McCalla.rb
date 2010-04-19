require 'Computer.Build'

# RTL-level description
Computer.Build "mccalla" do |computer|
  computer.address_width = 4
  
  computer.instruction "cla" do |i|
    i.move :A, 0
  end
  
  computer.instruction "cmp" do |i|
    i.move :A, complement(:A)
  end
  
  computer.instruction "inc" do |i|
    i.move :A, add(:A, 1)
  end

  computer.instruction "neg" do |i|
    i.move :A, complement(:A)
    i.move :A, add(:A, 1)
  end

  computer.instruction "add" do |i|
    i.move :MA, :IR
    i.move :A, add(:A, :MD)
  end

  computer.instruction "sub" do |i|
    i.move :MA, :IR
    i.move :A, subtract(:A, :MD)
  end

  computer.instruction "lda" do |i|
    i.move :MA, :IR
    i.move :A, :MD
  end

  computer.instruction "sta" do |i|
    i.move :MA, :IR
    i.move :MD, :A
  end

  computer.instruction "jmp" do |i|
    i.move :pc, :IR
  end
end
