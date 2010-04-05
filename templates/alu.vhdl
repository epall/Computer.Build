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
  rd        : IN std_logic
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
    CASE op IS
      WHEN "000" =>
        result <= operand_a and operand_b;
      WHEN "100" =>
        result <= operand_a and operand_b;
      WHEN "001" =>
        result <= operand_a or operand_b;
      WHEN "101" =>
        result <= operand_a or operand_b;
      WHEN "010" =>
        result <= operand_a + operand_b;
      WHEN "110" =>
        result <= operand_a - operand_b;
      WHEN "111" =>
        IF operand_a < operand_b THEN
          result <= "11111111";
        ELSE
          result <= "00000000";
        END IF;
      WHEN others =>
        result <= operand_a - operand_b;
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
