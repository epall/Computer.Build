(def instructions '())

(defmacro instruction [iname & rtl]
  (let [symname (symbol (str "instr-" iname)) body (list* 'instruction iname rtl)]
    `(def ~symname '(instruction ~iname ~@rtl))
    `(def instructions (cons (quote ~body) instructions))))

; RTL-level description
  (instruction "cla"
               (A <- 0))
  (instruction "cmp"
               (A <- (complement A)))
  (instruction "inc"
               (A <- (+ A 1)))
  (instruction "neg"
               (A <- (complement A))
               (A <- (+ A 1)))
  (instruction "add"
               (MA <- IR)
               (A <- (+ A MD)))
  (instruction "sub"
               (MA <- IR)
               (A <- (- A MD)))
  (instruction "lda"
               (MA <- IR)
               (A <- MD))
  (instruction "sta"
               (MA <- IR)
               (MD <- A))
