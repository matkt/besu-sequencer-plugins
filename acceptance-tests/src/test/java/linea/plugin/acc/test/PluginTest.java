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

package linea.plugin.acc.test;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import linea.plugin.acc.test.tests.web3j.generated.SimpleStorage;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

public class PluginTest extends AcceptanceTestBase {
  public static final int MAX_CALLDATA_SIZE = 1188; // contract has a call data size of 1160

  public static final int MAX_TX_GAS_LIMIT = DefaultGasProvider.GAS_LIMIT.intValue();
  public static final long CHAIN_ID = 1337L;
  private BesuNode minerNode;

  private void setUpWithMaxCalldata() throws Exception {

    // to debug into besu:
    // System.setProperty("acctests.runBesuAsProcess", "false");

    final List<String> cliOptions =
        List.of(
            "--plugin-linea-deny-list-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/emptyDenyList.txt"))
                    .getPath(),
            "--plugin-linea-max-tx-calldata-size=" + MAX_CALLDATA_SIZE,
            "--plugin-linea-max-block-calldata-size=" + MAX_CALLDATA_SIZE,
            "--plugin-linea-max-tx-gas-limit=" + DefaultGasProvider.GAS_LIMIT,
            "--plugin-linea-module-limit-file-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/noModuleLimits.json"))
                    .getPath());
    minerNode = besu.createMinerNodeWithExtraCliOptions("miner1", cliOptions);
    cluster.start(minerNode);
  }

  private void setUpWithDenyList() throws Exception {

    // To debug into besu:
    // System.setProperty("acctests.runBesuAsProcess", "false");

    final List<String> cliOptions =
        List.of(
            "--plugin-linea-deny-list-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/denyList.txt")).getPath(),
            "--plugin-linea-max-tx-calldata-size=2000000",
            "--plugin-linea-max-block-calldata-size=2000000",
            "--plugin-linea-max-tx-gas-limit=" + DefaultGasProvider.GAS_LIMIT,
            "--plugin-linea-module-limit-file-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/noModuleLimits.json"))
                    .getPath());
    minerNode = besu.createMinerNodeWithExtraCliOptions("miner1", cliOptions);
    cluster.start(minerNode);
  }

  private void setUpWithMaxTxGasLimit() throws Exception {

    // To debug into besu:
    // System.setProperty("acctests.runBesuAsProcess", "false");

    final List<String> cliOptions =
        List.of(
            "--plugin-linea-deny-list-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/emptyDenyList.txt"))
                    .getPath(),
            "--plugin-linea-max-tx-calldata-size=2000000",
            "--plugin-linea-max-block-calldata-size=2000000",
            "--plugin-linea-max-tx-gas-limit=" + MAX_TX_GAS_LIMIT,
            "--plugin-linea-module-limit-file-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/noModuleLimits.json"))
                    .getPath());
    minerNode = besu.createMinerNodeWithExtraCliOptions("miner1", cliOptions);
    cluster.start(minerNode);
  }

  private void setUpWithModuleLimits() throws Exception {

    // To debug into besu:
    // System.setProperty("acctests.runBesuAsProcess", "false");

    final List<String> cliOptions =
        List.of(
            "--plugin-linea-deny-list-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/emptyDenyList.txt"))
                    .getPath(),
            "--plugin-linea-max-tx-calldata-size=2000000",
            "--plugin-linea-max-block-calldata-size=2000000",
            "--plugin-linea-max-tx-gas-limit=" + MAX_TX_GAS_LIMIT,
            "--plugin-linea-module-limit-file-path="
                + Objects.requireNonNull(PluginTest.class.getResource("/moduleLimits.json"))
                    .getPath());
    minerNode = besu.createMinerNodeWithExtraCliOptions("miner1", cliOptions);
    cluster.start(minerNode);
  }

  @AfterEach
  public void stop() {
    cluster.stop();
    cluster.close();
  }

  @Test
  public void shouldLimitTxGas() throws Exception {
    setUpWithMaxTxGasLimit();
    final SimpleStorage simpleStorage = deploySimpleStorage();

    final Web3j web3j = minerNode.nodeRequests().eth();
    final String contractAddress = simpleStorage.getContractAddress();
    final Credentials credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    TransactionManager txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);

    final String txData = simpleStorage.set("hello").encodeFunctionCall();

    final String hashGood =
        txManager
            .sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(MAX_TX_GAS_LIMIT),
                contractAddress,
                txData,
                BigInteger.ZERO)
            .getTransactionHash();

    final String hashTooBig =
        txManager
            .sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(MAX_TX_GAS_LIMIT + 1),
                contractAddress,
                txData,
                BigInteger.ZERO)
            .getTransactionHash();

    TransactionReceiptProcessor receiptProcessor =
        new PollingTransactionReceiptProcessor(
            web3j, 4000L, TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);

    // make sure that a transaction that is not too big was mined
    final TransactionReceipt transactionReceipt =
        receiptProcessor.waitForTransactionReceipt(hashGood);
    assertThat(transactionReceipt).isNotNull();

    final EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(hashTooBig).send();
    assertThat(receipt.getTransactionReceipt()).isEmpty();
  }

  @Test
  public void shouldMineTransactions() throws Exception {
    setUpWithMaxCalldata();
    final SimpleStorage simpleStorage = deploySimpleStorage();

    List<String> accounts =
        List.of(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY, Accounts.GENESIS_ACCOUNT_TWO_PRIVATE_KEY);

    final Web3j web3j = minerNode.nodeRequests().eth();
    final List<Integer> numCaractersInStringList = List.of(150, 200, 400);

    numCaractersInStringList.forEach(
        num -> sendTransactionsWithGivenLengthPayload(simpleStorage, accounts, web3j, num));
  }

  @Test
  public void transactionIsNotMinedWhenTooBig() throws Exception {
    setUpWithMaxCalldata();
    final SimpleStorage simpleStorage = deploySimpleStorage();
    final Web3j web3j = minerNode.nodeRequests().eth();
    final String contractAddress = simpleStorage.getContractAddress();
    final Credentials credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    TransactionManager txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);

    final String txDataGood = simpleStorage.set("a".repeat(1200 - 80)).encodeFunctionCall();
    final String hashGood =
        txManager
            .sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                contractAddress,
                txDataGood,
                BigInteger.ZERO)
            .getTransactionHash();

    final String txDataTooBig = simpleStorage.set("a".repeat(1200 - 79)).encodeFunctionCall();
    final String hashTooBig =
        txManager
            .sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                contractAddress,
                txDataTooBig,
                BigInteger.ZERO)
            .getTransactionHash();

    TransactionReceiptProcessor receiptProcessor =
        new PollingTransactionReceiptProcessor(
            web3j, 4000L, TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);

    // make sure that a transaction that is not too big was mined
    final TransactionReceipt transactionReceipt =
        receiptProcessor.waitForTransactionReceipt(hashGood);
    assertThat(transactionReceipt).isNotNull();

    final EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(hashTooBig).send();
    assertThat(receipt.getTransactionReceipt()).isEmpty();
  }

  @Test
  public void deniedTransactionNotAddedToTxPool() throws Exception {
    setUpWithDenyList();

    final Credentials notDenied = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    final Credentials denied = Credentials.create(Accounts.GENESIS_ACCOUNT_TWO_PRIVATE_KEY);
    final Web3j miner = minerNode.nodeRequests().eth();

    BigInteger gasPrice = Convert.toWei("20", Convert.Unit.GWEI).toBigInteger();
    BigInteger gasLimit = BigInteger.valueOf(210000);

    // Make sure a sender on the deny list cannot add transactions to the pool
    RawTransactionManager transactionManager = new RawTransactionManager(miner, denied, CHAIN_ID);
    EthSendTransaction transactionResponse =
        transactionManager.sendTransaction(
            gasPrice, gasLimit, notDenied.getAddress(), "", BigInteger.ONE); // 1 wei

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo(
            "sender 0x627306090abab3a6e1400e9345bc60c78a8bef57 is blocked as appearing on the SDN or other legally prohibited list");

    // Make sure a transaction with a recipient on the deny list cannot be added to the pool
    transactionManager = new RawTransactionManager(miner, notDenied, CHAIN_ID);
    transactionResponse =
        transactionManager.sendTransaction(
            gasPrice, gasLimit, denied.getAddress(), "", BigInteger.ONE); // 1 wei

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo(
            "recipient 0x627306090abab3a6e1400e9345bc60c78a8bef57 is blocked as appearing on the SDN or other legally prohibited list");

    // Make sure a transaction calling a contract on the deny list (e.g. precompile) is not added to
    // the pool
    transactionResponse =
        transactionManager.sendTransaction(
            gasPrice,
            gasLimit,
            "0x000000000000000000000000000000000000000a",
            "0xdeadbeef",
            BigInteger.ONE); // 1 wei

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo("destination address is a precompile address and cannot receive transactions");
  }

  @Test
  public void transactionIsNotMinedWhenTooManyTraceLines() throws Exception {
    setUpWithModuleLimits();
    final SimpleStorage simpleStorage = deploySimpleStorage();
    final Web3j web3j = minerNode.nodeRequests().eth();
    final String contractAddress = simpleStorage.getContractAddress();
    final Credentials credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    final String txData = simpleStorage.add(BigInteger.valueOf(100)).encodeFunctionCall();

    final ArrayList<String> hashes = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final RawTransaction transaction =
          RawTransaction.createTransaction(
              CHAIN_ID,
              BigInteger.valueOf(i + 1),
              DefaultGasProvider.GAS_LIMIT,
              contractAddress,
              BigInteger.ZERO,
              txData,
              BigInteger.ONE,
              BigInteger.ONE);
      final byte[] signedTransaction = TransactionEncoder.signMessage(transaction, credentials);
      final EthSendTransaction response =
          web3j.ethSendRawTransaction(Numeric.toHexString(signedTransaction)).send();
      hashes.add(response.getTransactionHash());
    }

    TransactionReceiptProcessor receiptProcessor =
        new PollingTransactionReceiptProcessor(
            web3j,
            TransactionManager.DEFAULT_POLLING_FREQUENCY,
            TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);

    // make sure that there are no more than one transaction per block, because the limit for the
    // add module only allows for one of these transactions.
    final HashSet<Long> blockNumbers = new HashSet<>();
    for (String h : hashes) {
      Assertions.assertThat(
              blockNumbers.add(
                  receiptProcessor.waitForTransactionReceipt(h).getBlockNumber().longValue()))
          .isEqualTo(true);
    }
  }

  private void sendTransactionsWithGivenLengthPayload(
      final SimpleStorage simpleStorage,
      final List<String> accounts,
      final Web3j web3j,
      final int num) {
    final String contractAddress = simpleStorage.getContractAddress();
    final String txData =
        simpleStorage.set(RandomStringUtils.randomAlphabetic(num)).encodeFunctionCall();
    final List<String> hashes = new ArrayList<>();
    accounts.forEach(
        a -> {
          final Credentials credentials = Credentials.create(a);
          TransactionManager txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);
          for (int i = 0; i < 5; i++) {
            try {
              hashes.add(
                  txManager
                      .sendTransaction(
                          DefaultGasProvider.GAS_PRICE,
                          DefaultGasProvider.GAS_LIMIT,
                          contractAddress,
                          txData,
                          BigInteger.ZERO)
                      .getTransactionHash());
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });

    final HashMap<Long, Integer> txMap = new HashMap<>();
    TransactionReceiptProcessor receiptProcessor =
        new PollingTransactionReceiptProcessor(
            web3j,
            TransactionManager.DEFAULT_POLLING_FREQUENCY,
            TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);
    // CallData for the transaction for empty String is 68 and grows in steps of 32 with (String
    // size / 32)
    final int maxTxs = MAX_CALLDATA_SIZE / (68 + ((num + 31) / 32) * 32);

    // Wait for transaction to be mined and check that there are no more than maxTxs per block
    hashes.forEach(
        h -> {
          final TransactionReceipt transactionReceipt;

          try {
            transactionReceipt = receiptProcessor.waitForTransactionReceipt(h);
          } catch (IOException | TransactionException e) {
            throw new RuntimeException(e);
          }

          final long blockNumber = transactionReceipt.getBlockNumber().longValue();

          txMap.compute(
              blockNumber,
              (b, n) -> {
                if (n == null) {
                  return 1;
                }
                return n + 1;
              });

          // make sure that no block contained more than maxTxs
          assertThat(txMap.get(blockNumber)).isLessThanOrEqualTo(maxTxs);
        });
    // make sure that at least one block has maxTxs
    assertThat(txMap).containsValue(maxTxs);
  }

  private SimpleStorage deploySimpleStorage() throws Exception {
    final Web3j web3j = minerNode.nodeRequests().eth();
    final Credentials credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    TransactionManager txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);

    final RemoteCall<SimpleStorage> deploy =
        SimpleStorage.deploy(web3j, txManager, new DefaultGasProvider());
    return deploy.send();
  }
}
