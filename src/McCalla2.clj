(require 'computer-build)

; RTL-level description
(computer-build/build "mccalla2"
  ; options
  {:address-width 4}
  (instruction "cla"
               (A <- 0))
  (instruction "cmp"
               (A <- (complement A)))
  (instruction "inc"
               (A <- (+ A 1)))
  (instruction "neg"
               (A <- (complement A))
               (A <- (+ A 1)))
  (instruction "adddir"
               (MA <- (and IR 0x0F))
               (A <- (+ A MD)))
  (instruction "subdir"
               (MA <- (and IR 0x0F))
               (A <- (- A MD)))
  (instruction "addind"
               (MA <- (and IR 0x0F))
               (MA <- (+ MD 0))
               (A <- (+ A MD)))
  (instruction "subind"
               (MA <- (and IR 0x0F))
               (MA <- (+ MD 0))
               (A <- (- A MD)))
  (instruction "lda"
               (MA <- (and IR 0x0F))
               (A <- MD))
  (instruction "sta"
               (MA <- (and IR 0x0F))
               (MD <- A))
  (instruction "jmp"
               (PC <- (and IR 0x0F)))
  (instruction "bra0"
               (if (= A 0)
                 (PC <- (and IR 0x0F)))))
