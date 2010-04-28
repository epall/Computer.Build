LIBRARY ieee;
USE ieee.std_logic_1164.all;
USE ieee.numeric_std.all;

ENTITY alu IS
  GENERIC ( DATA_WIDTH : integer := 8 );
  PORT(
  clock     : IN std_logic;
  data_in   : IN std_logic_vector(DATA_WIDTH - 1 downto 0);
  data_out  : OUT std_logic_vector(DATA_WIDTH - 1 downto 0);
  op        : IN std_logic_vector(2 downto 0);
  wr_a      : IN std_logic;
  wr_b      : IN std_logic;
  rd        : IN std_logic;
  condition : OUT std_logic
      );
END alu;

ARCHITECTURE arch OF alu IS
  SIGNAL operand_a : SIGNED(DATA_WIDTH-1 downto 0);
  SIGNAL operand_b : SIGNED(DATA_WIDTH-1 downto 0);
  SIGNAL result    : SIGNED(DATA_WIDTH-1 downto 0);
BEGIN
  WITH rd SELECT
    data_out <= std_logic_vector(result) WHEN '1',
                "ZZZZZZZZ" WHEN OTHERS;

  PROCESS(op, operand_a, operand_b)
  BEGIN
    condition <= '0';
    result <= "00000000";
    CASE op IS
      WHEN "000" =>
        result <= operand_a;
      WHEN "001" =>
        result <= operand_a and operand_b;
      WHEN "010" =>
        result <= operand_a or operand_b;
      WHEN "011" =>
        result <= not operand_a;
      WHEN "100" =>
        result <= operand_a + operand_b;
      WHEN "101" =>
        result <= operand_a - operand_b;
      WHEN "110" =>
        IF operand_a = operand_b THEN
          condition <= '1';
        END IF;
      WHEN "111" =>
        IF operand_a < operand_b THEN
          condition <= '1';
        END IF;
    END CASE;
  END PROCESS;

  PROCESS(clock)
  BEGIN
    IF clock'EVENT AND clock='0' THEN
      IF wr_a = '1' THEN
        operand_a <= signed(data_in);
      ELSIF wr_b = '1' THEN
        operand_b <= signed(data_in);
      END IF;
    END IF;
  END PROCESS;
END arch;
