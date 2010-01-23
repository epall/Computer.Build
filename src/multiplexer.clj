(use 'computer-build.vhdl)

(generate-vhdl
  `(entity "Mux"
           ;; ports
           [
            [:I3 :in "std_logic_vector(2 downto 0)"]
            [:I2 :in "std_logic_vector(2 downto 0)"]
            [:I1 :in "std_logic_vector(2 downto 0)"]
            [:I0 :in "std_logic_vector(2 downto 0)"]
            [:S :in "std_logic_vector(1 downto 0)"]
            [:O :out "std_logic_vector(2 downto 0)"]
            ]
           ;; architecture
           (process [:I3 :I2 :I1 :I0 :S]
                    (case :S
                          "00" (assign :O :I0)
                          "01" (assign :O :I1)
                          "10" (assign :O :I2)
                          "11" (assign :O :I3)
                          "others" (assign :O "ZZZ")
                          )
                    )
           )
  )
