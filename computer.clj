; When run through Computer.Build, this should produce valid VHDL
; that implements the specified computer.
(computer
  (instruction "add" [x y] (store y (+ (load x) (load y))))
  (instruction "subtract" [x y] (store y (- (load x) (load y))))
  (instruction "move" [addr1 addr2]
    (store addr2 (load addr1)))
  (instruction "jump" [addr] (set-register :pc addr))
  (instruction "jeq" [x y addr]
    (if (= (load x) (load y))
      (set-register :pc addr))))
