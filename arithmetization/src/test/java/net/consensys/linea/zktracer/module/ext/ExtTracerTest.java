/*
 * Copyright Consensys Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.consensys.linea.zktracer.module.ext;

import java.util.List;
import java.util.stream.Stream;

import net.consensys.linea.zktracer.module.Module;
import net.consensys.linea.zktracer.testing.DynamicTests;
import net.consensys.linea.zktracer.testing.OpcodeCall;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ExtTracerTest {
  private static final Module MODULE = new Ext();

  private static final DynamicTests DYN_TESTS = DynamicTests.forModule(MODULE);

  @TestFactory
  Stream<DynamicTest> runDynamicTests() {
    return DYN_TESTS
        .testCase("non random arguments test", provideNonRandomArguments())
        .testCase("zero value test", provideZeroValueTest())
        // TODO: re-enable
        // .testCase("modulus zero value arguments test", provideModulusZeroValueArguments())
        .testCase("tiny value arguments test", provideTinyValueArguments())
        .testCase("max value arguments test", provideMaxValueArguments())
        .run();
  }

  private List<OpcodeCall> provideNonRandomArguments() {
    return DYN_TESTS.newModuleArgumentsProvider(
        (testCases, opCode) -> {
          for (int k = 1; k <= 4; k++) {
            for (int i = 1; i <= 4; i++) {
              testCases.add(
                  new OpcodeCall(
                      opCode, List.of(UInt256.valueOf(i), UInt256.valueOf(k), UInt256.valueOf(k))));
            }
          }
        });
  }

  private List<OpcodeCall> provideZeroValueTest() {
    return DYN_TESTS.newModuleArgumentsProvider(
        (testCases, opCode) -> {
          testCases.add(
              new OpcodeCall(
                  opCode, List.of(UInt256.valueOf(6), UInt256.valueOf(12), UInt256.valueOf(0))));
        });
  }

  private List<OpcodeCall> provideModulusZeroValueArguments() {
    return DYN_TESTS.newModuleArgumentsProvider(
        (testCases, opCode) -> {
          testCases.add(
              new OpcodeCall(
                  opCode, List.of(UInt256.valueOf(0), UInt256.valueOf(1), UInt256.valueOf(1))));
        });
  }

  private List<OpcodeCall> provideTinyValueArguments() {
    return DYN_TESTS.newModuleArgumentsProvider(
        (testCases, opCode) -> {
          testCases.add(
              new OpcodeCall(
                  opCode, List.of(UInt256.valueOf(6), UInt256.valueOf(7), UInt256.valueOf(13))));
        });
  }

  private List<OpcodeCall> provideMaxValueArguments() {
    return DYN_TESTS.newModuleArgumentsProvider(
        (testCases, opCode) -> {
          testCases.add(
              new OpcodeCall(
                  opCode, List.of(UInt256.MAX_VALUE, UInt256.MAX_VALUE, UInt256.MAX_VALUE)));
        });
  }
}
