(use 'computer-build.state-machine 'computer-build.vhdl 'clojure.contrib.pprint)

(def ide-controller (state-machine "ide_controller"
                                   ; inputs
                                   {:reset std-logic
                                    :instr_command (std-logic-vector 7 0)
                                    :instr_address (std-logic-vector 7 0)
                                    :instr_data (std-logic-vector 15 0)
                                    :available_instr std-logic
                                    :ide_data_in (std-logic-vector 15 0)
                                    :ide_ready std-logic
                                    :ide_int_request std-logic
                                    :ide_16_bit_n std-logic
                                    :ide_dasp_n std-logic
                                    }
                                   ; outpus
                                   {:result_command (std-logic-vector 7 0)
                                    :result_status (std-logic-vector 7 0)
                                    :result_data (std-logic-vector 15 0)
                                    :read_instr std-logic
                                    :write_result std-logic
                                    :ide_reset_n std-logic
                                    :ide_data_out (std-logic-vector 15 0)
                                    :ide_data_write_n std-logic
                                    :ide_data_read_n std-logic
                                    :ide_address (std-logic-vector 2 0)
                                    :ide_cs_1f0_n std-logic
                                    :ide_cs_3f0_n std-logic
                                    :ide_data_OE std-logic
                                    }
                                   ; signals
                                   {:command_buffer (std-logic-vector 7 0)
                                    :address_buffer (std-logic-vector 7 0)
                                    :data_buffer (std-logic-vector 15 0)
                                    }
                                   ; reset
                                   '[
                                     (goto :wait)
                                     (low :ide_reset_n)
                                     (low :read_instr)
                                     (low :write_result)
                                     (high :ide_data_write_n)
                                     (high :ide_data_read_n)
                                     (high :ide_cs_1f0_n)
                                     (high :ide_cs_3f0_n)
                                     (<= :ide_address "000")
                                     (<= :ide_data_out "0000000000000000")
                                     (<= :result_command "00000000")
                                     (<= :result_status "00000000")
                                     (<= :result_data "0000000000000000")
                                     ]
                                   ; states
                                   {:wait
                                    '[
                                      (high :ide_reset_n)
                                      (low :write_result)
                                      (<= :read_instr, :available_instr)]
                                    :decode
                                    '[
                                      (<= :command_buffer :instr_command)
                                      (<= :address_buffer, :instr_address)
                                      (<= :data_buffer, :instr_data)
                                      (low :read_instr)
                                      (case :instr_command
                                            "00000000"
                                            [(<= :result_command, :instr_command)
                                               (<= :result_status, "00000000")
                                               (<= :result_data, "0000000000000000")]
                                            "00000011"
                                            [(<= :result_command, :instr_command)
                                               (<= :ide_address 0 :instr_address 0)
                                               (<= :ide_address 1 :instr_address 1)
                                               (<= :ide_address 2 :instr_address 2)]
                                            "00000010"
                                            [(<= :result_command, :instr_command)
                                               (<= :ide_address 0 :instr_address 0)
                                               (<= :ide_address 1 :instr_address 1)
                                               (<= :ide_address 2 :instr_address 2)
                                               (low :ide_cs_1f0_n)])]
                                    :writestatus
                                    '[
                                     (high :write_result)
                                      (high :ide_cs_1f0_n)
                                      (high :ide_data_read_n)]
                                    :action
                                    '[
                                      (if-else (= :instr_command "00000011")
                                        [(<= :result_data :ide_data_in)]
                                        [(<= :result_data, "0000000000000000")])
                                      (low :ide_data_OE)]
                                    :data_on_bus
                                    '[
                                      (if (= :command_buffer, "00000011")
                                        [(high :ide_data_write_n)
                                        (low :ide_data_read_n)])]
                                    }
                                   ; transitions (from condition to)
                                   [
                                    '(:wait (equal :available_instr "1") :decode)
                                    '(:decode (equal :instr_command "00000000") :writestatus)
                                    '(:decode (equal :instr_command "00000011") :data_on_bus)
                                    '(:decode (equal :instr_command "00000010") :data_on_bus)
                                    '(:writestatus :wait)
                                    '(:action :writestatus)
                                    '(:data_on_bus (equal :command_buffer, "00000011") :action)
                                    ]))

(pprint ide-controller)
(println "=================")
(generate-vhdl ide-controller)

