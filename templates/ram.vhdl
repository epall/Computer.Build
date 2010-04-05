LIBRARY ieee;
USE ieee.std_logic_1164.ALL;
USE ieee.numeric_std.ALL;

ENTITY ram IS
  GENERIC
  (
    ADDRESS_WIDTH  : integer := 5;
    DATA_WIDTH     : integer := 8
  );
  PORT
  (
    clock    : IN  std_logic;
    data_in  : IN  std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    data_out : OUT std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);
    address  : IN  std_logic_vector(ADDRESS_WIDTH - 1 DOWNTO 0);
    wr_data  : IN  std_logic;
    wr_addr  : IN  std_logic;
    rd       : IN  std_logic
);
END ram;

ARCHITECTURE rtl OF ram IS
  TYPE RAM IS ARRAY(0 TO 2 ** ADDRESS_WIDTH - 1) OF std_logic_vector(DATA_WIDTH - 1 DOWNTO 0);

  SIGNAL ram_block : RAM;
  SIGNAL addr_cache : std_logic_vector(ADDRESS_WIDTH - 1 DOWNTO 0);
BEGIN
  WITH rd SELECT
    data_out <= ram_block(to_integer(unsigned(addr_cache))) WHEN '1',
                "ZZZZZZZZ" WHEN OTHERS;

  PROCESS (clock)
  BEGIN
    IF clock'EVENT AND clock = '0' THEN
      IF(wr_data = '1') THEN
        ram_block(to_integer(unsigned(addr_cache))) <= data_in;
      ELSIF(wr_addr = '1') THEN
        addr_cache <= address;
      END IF;
    END IF;
  END PROCESS;
END rtl;
