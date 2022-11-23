package ir.milad.customer.domain.wallet;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER;

class WalletTest {

    public static final Money _1M = Money.of(1_000_000L);
    public static final Money _2M = Money.of(2_000_000L);
    public static final Money _3M = Money.of(3_000_000L);
    public static final Money _4M = Money.of(4_000_000L);
    public static final Money _7M = Money.of(7_000_000L);
    public static final Money _9M = Money.of(9_000_000L);
    public static final Money _10M = Money.of(10_000_000L);

    @Nested
    @DisplayName("given wallet charge functionality with Money(7_000_000)")
    class ChargeTest {

        @Test
        @DisplayName("and wallet has ZERO cash or blocked then buyingPower on all delays should be Money(7_000_000)")
        public void chargeOnFreshWallet() {
            Wallet wallet = new Wallet();

            wallet.charge(_7M);

            Arrays.stream(SettlementDelay.values()).forEach(delay ->
                    assertThat(wallet.buyingPower(delay)).isEqualTo(_7M)
            );
        }

        @Test
        @DisplayName("and wallet has been charged by 2_000_000 then buyingPower on all delays should be Money(9_000_000)")
        public void chargeOnPreChargedWallet() {
            Wallet wallet = new Wallet();
            wallet.charge(_7M);
            wallet.charge(_2M);

            Arrays.stream(SettlementDelay.values()).forEach(delay ->
                    assertThat(wallet.buyingPower(delay)).isEqualTo(_9M)
            );
        }
    }

    @Nested
    @DisplayName("given block with 3_000_000 amount")
    class BlockTests {

        @Nested
        @DisplayName("When wallet is empty")
        class EmptyWallet {
            @ParameterizedTest(name = "and delay = " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + " then should throw InsufficientFundsException")
            @DisplayName("blocking from empty wallet")
            @EnumSource(SettlementDelay.class)
            public void blockingFromEmptyWallet(SettlementDelay delay) {
                assertThatExceptionOfType(InsufficientFundsException.class)
                        .isThrownBy(() -> new Wallet().block(_3M, delay));
            }
        }

        @Nested
        @DisplayName("When wallet is charged by 7_000_000")
        class WalletDef {

            @DisplayName("and amount is 3_000_000")
            @ParameterizedTest(name = "and delay = " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + " then buyingPower shouldBe 4_000_000 for given delay and higher")
            @EnumSource(value = SettlementDelay.class)
            public void test1(SettlementDelay delay) {
                var wallet = new Wallet();
                wallet.charge(_7M);

                wallet.block(_3M, delay);

                greaterThanAndEqual(delay).forEach(settlementDelay ->
                        assertThat(wallet.buyingPower(settlementDelay)).isEqualTo(_4M)
                );
            }
        }
    }

    @Nested
    @DisplayName("given spend with 3M")
    class SpendTests {
        @Nested
        @DisplayName("when wallet is charged by 7M and block is called by 3M amount")
        class WalletDef {
            @DisplayName("then")
            @ParameterizedTest(name = "unblock(3M, " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + ") should throw exception")
            @EnumSource(value = SettlementDelay.class, names = {"T_PLUS_1", "T_PLUS_2", "T_PLUS_3"})
            public void test1(SettlementDelay delay) {
                var wallet = new Wallet();
                wallet.charge(_7M);
                wallet.block(_3M, delay);

                wallet.spend(_3M, delay);

                assertThatExceptionOfType(InsufficientFundsException.class)
                        .isThrownBy(() -> wallet.unblock(_3M, delay));
            }
        }

        @Nested
        @DisplayName("when wallet is t0 = 500_000, t1 = 100_000, t2 = 1_000_000")
        class WalletDef2 {
            @Nested
            @DisplayName("and block(1.6M, T2) -> charge(4M) -> block(1M, T1) -> block(1.5M, T2)")
            class Prepare {
                Wallet wallet;

                @BeforeEach
                public void beforeEach() {
                    wallet = new Wallet();
                    wallet.charge(Money.of(500_000L));
                    wallet.deposit(Money.of(100_000L), SettlementDelay.T_PLUS_1);
                    wallet.deposit(_1M, SettlementDelay.T_PLUS_2);
                    wallet.block(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);
                    wallet.charge(_4M);
                    wallet.block(_1M, SettlementDelay.T_PLUS_1);
                    wallet.block(Money.of(1_500_000L), SettlementDelay.T_PLUS_2);
                }

                @Test
                @DisplayName("then spend(1.6M, T2) should make B0 = 2.5M, B1,B2 = 0 and T2 -> T1 debt = 0 and T2 -> T0 debt = 1.5M")
                public void test() {
                    wallet.spend(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);

                    var walletsAndDebts = getWalletPrivateFields();
                    Map<SettlementDelay, InternalWallet> delayWallets = walletsAndDebts._1;
                    Wallet.DebtSupervisor debtSupervisor = walletsAndDebts._2;

                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_0).getBlocked()).isEqualTo(new Balance(2_500_000L));
                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_1).getBlocked()).isEqualTo(new Balance(0L));
                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_2).getBlocked()).isEqualTo(new Balance(0L));
                    assertThat(
                            debtSupervisor.get(SettlementDelay.T_PLUS_1.asLender(), SettlementDelay.T_PLUS_2.asBorrower())
                    ).isEqualTo(Money.ZERO);
                    assertThat(
                            debtSupervisor.get(SettlementDelay.T_PLUS_0.asLender(), SettlementDelay.T_PLUS_2.asBorrower())
                    ).isEqualTo(Money.of(1_500_000L));
                }

                @Test
                @DisplayName("then spend(1.6M, T1) should throw error")
                public void test2() {
                    assertThatExceptionOfType(InsufficientFundsException.class)
                            .isThrownBy(() -> wallet.spend(Money.of(1_600_000L), SettlementDelay.T_PLUS_1));
                }

                @Test
                @DisplayName("then spend(1M, T1) should ")
                public void test3() {
                    wallet.spend(Money.of(1_000_000L), SettlementDelay.T_PLUS_1);

                    var walletsAndDebts = getWalletPrivateFields();
                    Map<SettlementDelay, InternalWallet> delayWallets = walletsAndDebts._1;
                    Wallet.DebtSupervisor debtSupervisor = walletsAndDebts._2;

                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_0).getBlocked()).isEqualTo(new Balance(2_100_000L));
                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_1).getBlocked()).isEqualTo(new Balance(0L));
                    assertThat(delayWallets.get(SettlementDelay.T_PLUS_2).getBlocked()).isEqualTo(new Balance(1_000_000L));
                    assertThat(
                            debtSupervisor.get(SettlementDelay.T_PLUS_0.asLender(), SettlementDelay.T_PLUS_1.asBorrower())
                    ).isEqualTo(Money.of(100_000L));
                    assertThat(
                            debtSupervisor.get(SettlementDelay.T_PLUS_0.asLender(), SettlementDelay.T_PLUS_2.asBorrower())
                    ).isEqualTo(Money.of(2_000_000L));
                    assertThat(
                            debtSupervisor.get(SettlementDelay.T_PLUS_1.asLender(), SettlementDelay.T_PLUS_2.asBorrower())
                    ).isEqualTo(Money.of(100_000L));
                }

                private Tuple2<Map<SettlementDelay, InternalWallet>, Wallet.DebtSupervisor> getWalletPrivateFields() {
                    Map<SettlementDelay, InternalWallet> delayWallets;
                    Wallet.DebtSupervisor debtSupervisor;
                    try {
                        Field delayWalletsField = wallet.getClass().getDeclaredField("delayWallets");
                        Field debtSupervisorField = wallet.getClass().getDeclaredField("debtSupervisor");
                        delayWalletsField.setAccessible(true);
                        debtSupervisorField.setAccessible(true);
                        delayWallets = (Map<SettlementDelay, InternalWallet>) delayWalletsField.get(wallet);
                        debtSupervisor = (Wallet.DebtSupervisor) debtSupervisorField.get(wallet);

                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    return Tuple.of(delayWallets, debtSupervisor);
                }
            }
        }
    }

    @Nested
    @DisplayName("given deposit")
    class DepositTests {

        @Nested
        @DisplayName("when wallet is clear")
        class EmptyWallet {
            @ParameterizedTest(name = "buyingPower on " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + " and higher should increase")
            @DisplayName("then ")
            @EnumSource(value = SettlementDelay.class)
            public void test1(SettlementDelay delay) {
                var wallet = new Wallet();

                wallet.deposit(_1M, delay);

                greaterThanAndEqual(delay).forEach(settlementDelay ->
                        assertThat(wallet.buyingPower(settlementDelay)).isEqualTo(_1M)
                );
            }

            @ParameterizedTest(name = "buyingPower on " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + " and lower should not change")
            @DisplayName("then ")
            @EnumSource(value = SettlementDelay.class)
            public void test2(SettlementDelay delay) {
                var wallet = new Wallet();

                wallet.deposit(_1M, delay);

                lessThan(delay).forEach(settlementDelay ->
                        assertThat(wallet.buyingPower(settlementDelay)).isEqualTo(Money.ZERO)
                );
            }
        }

        @Nested
        @DisplayName("when wallet is charged by 7M and block(3M, T_PLUS_1), block(3M, T_PLUS_2)")
        class WalletDef {
            public Wallet wallet;

            @BeforeEach
            public void beforeEach() {
                wallet = new Wallet();
                wallet.charge(_7M);
                wallet.block(_3M, SettlementDelay.T_PLUS_1);
                wallet.block(_3M, SettlementDelay.T_PLUS_2);
            }

            @ParameterizedTest(name = "deposit(1M, " + ARGUMENTS_WITH_NAMES_PLACEHOLDER + ")")
            @DisplayName("then buyingPower(T_PLUS_0) should be 2M")
            @EnumSource(value = SettlementDelay.class, names = {"T_PLUS_1", "T_PLUS_2"})
            public void test1(SettlementDelay delay) {
                wallet.deposit(_1M, delay);

                assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_0)).isEqualTo(_2M);
            }

            @DisplayName("and deposit(1M, T_PLUS_3) then buyingPower(T_PLUS_0) should be 1M")
            @Test
            public void test2() {
                wallet.deposit(_1M, SettlementDelay.T_PLUS_3);

                assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_0)).isEqualTo(_1M);
            }
        }

        @Nested
        @DisplayName("when wallet is t0 = 500_000, t1 = 100_000, t2 = 1M")
        class WalletDef2 {
            Wallet wallet = new Wallet();
            {
                wallet.charge(Money.of(500_000L));
                wallet.deposit(Money.of(100_000L), SettlementDelay.T_PLUS_1);
                wallet.deposit(_1M, SettlementDelay.T_PLUS_2);
            }
            @Nested
            @DisplayName("and block(1.6M, T2) -> charge(4M) ")
            class Prepare {
                {
                    wallet.block(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);
                    wallet.charge(_4M);
                }
                @Nested
                @DisplayName("and block(2M, T1) -> block(2M, T2) ")
                class And1 {
                    {
                        wallet.block(_2M, SettlementDelay.T_PLUS_1);
                        wallet.block(_2M, SettlementDelay.T_PLUS_2);
                    }
                    @Test
                    @DisplayName("then deposit(1M, T_PLUS_2) should result in buyingPower(T_PLUS_0) = 1M")
                    public void test() {
                        wallet.deposit(_1M, SettlementDelay.T_PLUS_2);

                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_0)).isEqualTo(_1M);
                    }
                }

                @Nested
                @DisplayName("and block(1M, T1) -> block(1.5M, T2)")
                class And2 {
                    {
                        wallet.block(_1M, SettlementDelay.T_PLUS_1);
                        wallet.block(Money.of(1_500_000L), SettlementDelay.T_PLUS_2);
                    }

                    @Test
                    @DisplayName("then deposit(10M, T2) should result in buyingPower(T0) = 3.6M")
                    public void test1() {
                        wallet.deposit(_10M, SettlementDelay.T_PLUS_2);

                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_0)).isEqualTo(Money.of(3_600_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_1)).isEqualTo(Money.of(3_600_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_2)).isEqualTo(Money.of(11_500_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_3)).isEqualTo(Money.of(11_500_000L));
                    }

                    @Test
                    @DisplayName("then deposit(10M, T1) should result to bp(T0) = 2.5M, bp(T1),   bp(T2) = 11.5M")
                    public void test2() {
                        wallet.deposit(_10M, SettlementDelay.T_PLUS_1);

                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_0)).isEqualTo(Money.of(2_500_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_1)).isEqualTo(Money.of(11_500_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_2)).isEqualTo(Money.of(11_500_000L));
                        assertThat(wallet.buyingPower(SettlementDelay.T_PLUS_3)).isEqualTo(Money.of(11_500_000L));
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("given unblock with 1.6M")
    class UnblockTests {

        @Nested
        @DisplayName("when wallet is t0 = 500_000, t1 = 100_000, t2 = 1_000_000")
        class WalletDef {
            Wallet wallet = new Wallet();
            {
                wallet.charge(Money.of(500_000L));
                wallet.deposit(Money.of(100_000L), SettlementDelay.T_PLUS_1);
                wallet.deposit(_1M, SettlementDelay.T_PLUS_2);
            }
            @Nested
            @DisplayName("and block(1.6M, T_PLUS_2) -> charge(4M)")
            class Prepare1 {
                {
                    wallet.block(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);
                    wallet.charge(_4M);
                }

                @Nested
                @DisplayName("and block(2M, T_PLUS_1) -> block(2M, T_PLUS_2)")
                class And1 {
                    {
                        wallet.block(_2M, SettlementDelay.T_PLUS_1);
                        wallet.block(_2M, SettlementDelay.T_PLUS_2);
                    }

                    @DisplayName("then ")
                    @ParameterizedTest(name = "availableBalance(" + ARGUMENTS_WITH_NAMES_PLACEHOLDER + ") should be 1.6M")
                    @EnumSource(SettlementDelay.class)
                    public void test(SettlementDelay buyingPowerDelay) {
                        wallet.unblock(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);

                        assertThat(wallet.buyingPower(buyingPowerDelay)).isEqualTo(Money.of(1_600_000L));
                    }
                }

                @Nested
                @DisplayName("and block(2M, T_PLUS_1) -> block(1M, T_PLUS_2)")
                class And2 {
                    {
                        wallet.block(_2M, SettlementDelay.T_PLUS_1);
                        wallet.block(_1M, SettlementDelay.T_PLUS_2);
                    }

                    @DisplayName("then unblock(1.6M, T2) should result to")
                    @ParameterizedTest(name = "availableBalance(" + ARGUMENTS_WITH_NAMES_PLACEHOLDER + ") should be 2.6M")
                    @EnumSource(SettlementDelay.class)
                    public void test(SettlementDelay buyingPowerDelay) {
                        wallet.unblock(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);

                        assertThat(wallet.buyingPower(buyingPowerDelay)).isEqualTo(Money.of(2_600_000L));
                    }
                }

                @Nested
                @DisplayName("and block(1M, T_PLUS_1) -> block(1.5M, T_PLUS_2")
                class And3 {
                    {
                        wallet.block(_1M, SettlementDelay.T_PLUS_1);
                        wallet.block(Money.of(1_500_000L), SettlementDelay.T_PLUS_2);
                    }
                    @DisplayName("then unblock(1.6M, T2) should result to")
                    @Test
                    public void test() {
                        wallet.unblock(Money.of(1_600_000L), SettlementDelay.T_PLUS_2);

                        Arrays.stream(SettlementDelay.values()).forEach(delay ->
                                assertThat(wallet.buyingPower(delay)).isEqualTo(Money.of(3_100_000L))
                        );
                    }
                }
            }
        }
    }
    private List<SettlementDelay> greaterThanAndEqual(SettlementDelay delay) {
        return switch (delay) {
            case T_PLUS_0 ->
                    List.of(SettlementDelay.T_PLUS_0, SettlementDelay.T_PLUS_1, SettlementDelay.T_PLUS_2, SettlementDelay.T_PLUS_3);
            case T_PLUS_1 -> List.of(SettlementDelay.T_PLUS_1, SettlementDelay.T_PLUS_2, SettlementDelay.T_PLUS_3);
            case T_PLUS_2 -> List.of(SettlementDelay.T_PLUS_2, SettlementDelay.T_PLUS_3);
            case T_PLUS_3 -> List.of(SettlementDelay.T_PLUS_3);
        };
    }

    private List<SettlementDelay> lessThan(SettlementDelay delay) {
        return switch (delay) {
            case T_PLUS_0 -> List.of();
            case T_PLUS_1 -> List.of(SettlementDelay.T_PLUS_0);
            case T_PLUS_2 -> List.of(SettlementDelay.T_PLUS_0, SettlementDelay.T_PLUS_1);
            case T_PLUS_3 -> List.of(SettlementDelay.T_PLUS_0, SettlementDelay.T_PLUS_1, SettlementDelay.T_PLUS_2);
        };
    }
}