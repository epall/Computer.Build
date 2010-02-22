LIBRARY ieee;
USE ieee.std_logic_1164.ALL;
USE ieee.numeric_std.ALL;

ENTITY ram IS
  GENERIC
  (
    ADDRESS_WIDTH  : integer := 4;
    DATA_WIDTH     : integer := 8
  );
  PORT
  (
    clock    : IN  std_logic;
    data_in  : IN  std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    data_out : OUT std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    address  : IN  std_logic_vector(ADDRESS_WIDTH - 1 DOWNTO 0);
    wr       : IN  std_logic;
    rd       : IN  std_logic
);
END ram;

ARCHITECTURE rtl OF ram IS
  TYPE RAM IS ARRAY(0 TO 2 ** ADDRESS_WIDTH - 1) OF std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);

  SIGNAL ram_block : RAM;
BEGIN
  PROCESS (clock)
  BEGIN
      IF (rd = '1') THEN
        data_out <= ram_block(to_integer(unsigned(address)));
      ELSE
        data_out <= "ZZZZZZZZ";
      END IF;

    IF (clock'EVENT AND clock = '0' AND wr = '1') THEN
       ram_block(to_integer(unsigned(address))) <= data_in;
    END IF;
  END PROCESS;
END rtl;
