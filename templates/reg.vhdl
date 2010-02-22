LIBRARY ieee;
USE ieee.std_logic_1164.ALL;
USE ieee.numeric_std.ALL;

ENTITY reg IS
  GENERIC
  (
    DATA_WIDTH  : integer := 8
  );
  PORT
  (
    clock        : IN  std_logic;
    data_in      : IN  std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    data_out      : OUT std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    wr           : IN  std_logic;
    rd           : IN  std_logic
  );
END reg;

ARCHITECTURE rtl OF reg IS
  SIGNAL regval : std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
BEGIN
  PROCESS (clock)
  BEGIN
  IF rd = '1' THEN
    data_out <= regval;
  ELSE
    data_out <= "ZZZZZZZZ";
  END IF;

  IF (clock'event AND clock = '0' AND wr = '1') THEN
    regval <= data_in;
    END IF;
  END PROCESS;
END rtl;

