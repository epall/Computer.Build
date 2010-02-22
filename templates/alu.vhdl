LIBRARY ieee;
USE ieee.std_logic_1164.all;
USE ieee.numeric_std.all;

ENTITY alu IS
  GENERIC ( DATA_WIDTH : integer := 8 );
  PORT(
  data_in   : IN std_logic_vector(DATA_WIDTH - 1 downto 0);
  data_out  : OUT std_logic_vector(DATA_WIDTH - 1 downto 0);
  op        : IN std_logic_vector(2 downto 0);
  clock     : IN std_logic;
  ld_a      : IN std_logic;
  ld_b      : IN std_logic;
  write_f   : IN std_logic
      );
END alu;

ARCHITECTURE arch OF alu IS
  SIGNAL operand_a : SIGNED;
  SIGNAL operand_b : SIGNED;
  SIGNAL result    : SIGNED;
BEGIN
  PROCESS(write_f)
  BEGIN
    IF write_f = '1' THEN
      data_out <= std_logic_vector(result);
    ELSE
      data_out <= "ZZZZZZZZ";
    END IF;
  END PROCESS;

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
      IF ld_a = '1' THEN
        operand_a <= signed(data_in);
      ELSIF ld_b = '1' THEN
        operand_b <= signed(data_in);
      END IF;
    ELSIF clock'EVENT AND clock='1' THEN
      IF write_f = '1' THEN
        data_out <= std_logic_vector(result);
      ELSE
        data_out <= "00000000";
      END IF;
    END IF;
  END PROCESS;
END arch;
