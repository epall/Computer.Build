LIBRARY ieee;
USE ieee.std_logic_1164.ALL;
USE ieee.numeric_std.ALL;

ENTITY program_counter IS
  GENERIC
  (
    DATA_WIDTH  : integer := 8
  );
  PORT
  (
    clock        : IN  std_logic;
    data_in      : IN  std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    data_out     : OUT std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    wr           : IN  std_logic;
    rd           : IN  std_logic;
    inc          : IN  std_logic
  );
END program_counter;

ARCHITECTURE rtl OF program_counter IS
  SIGNAL regval : unsigned(DATA_WIDTH - 1 DOWNTO 0);
BEGIN
  WITH rd SELECT
    data_out <= std_logic_vector(regval) WHEN '1',
                "ZZZZZZZZ" WHEN OTHERS;
  
  PROCESS (clock)
  BEGIN
    IF clock'EVENT AND clock = '0' THEN
      IF wr = '1' THEN
        regval <= unsigned(data_in);
      ELSIF inc = '1' THEN
        regval <= regval + 1;
      END IF;
    END IF;
  END PROCESS;
END rtl;

