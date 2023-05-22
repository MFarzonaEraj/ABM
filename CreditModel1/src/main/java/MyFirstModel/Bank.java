package MyFirstModel;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

import java.util.List;

public class Bank extends Agent<CreditScoringModel.Globals> {

    @Variable public int debt = 0;
    @Variable public int assets = 10000000;
    @Variable
    public int equity() {
        return assets - debt;
    }

    @Variable public int nbCredits = 0;

    //Чистая процентная маржа (NIM) — это показатель, сравнивающий чистый процентный доход, который финансовая фирма получает от кредитных продуктов, таких как кредиты и ипотечные кредиты, с исходящими процентами, которые она выплачивает держателям сберегательных счетов и депозитных сертификатов (CD)
    public static final double NIM = 0.25;
    private int termInYears = 4; // Should be large for mortgage
    private int termInMonths = termInYears * 12;


    //нарушения
    int impairments = 0;
    int income = 0;
    @Variable(name = "Stage 1 Provisions")
    public double stage1Provisions = 0.0;

    @Variable(name = "Stage 2 Provisions")
    public double stage2Provisions = 0.0;
    private double interest() {
        return 1 + (getGlobals().interestRate / 100);
    }
    private static Action<Bank> action(SerializableConsumer<Bank> consumer) {
        return Action.create(Bank.class, consumer);
    }
    static Action<Bank> processApplication =
            action(
                    bank ->
                            bank.getMessagesOfType(Messages.CreditApplication.class).stream()

                                    .forEach(
                                            m -> {
                                                int totalAmount =
                                                        (int) (m.amount * Math.pow(bank.interest(), bank.termInYears));

                                                bank.send(
                                                                Messages.ApplicationSuccessful.class,
                                                                newMessage -> {
                                                                    newMessage.amount = totalAmount;
                                                                    newMessage.termInMonths = bank.termInMonths;
                                                                    newMessage.repayment = totalAmount / bank.termInMonths;
                                                                })
                                                        .to(m.getSender());
                                                bank.nbCredits += 1;
                                                bank.assets += m.amount;
                                                bank.debt += m.amount;
                                            }));

    void accumulateIncome() {
        income = 0;
        getMessagesOfType(Messages.Payment.class).forEach(payment -> income += payment.repayment);

        assets += (income * NIM);
    }

    void processArrears() {
        List<Messages.Arrears> arrears = getMessagesOfType(Messages.Arrears.class);
        // Count bad loans
        calcBadLoans(arrears);
        // Calculate provisions
        double stage1Provisions = calcStageOneProvisions(arrears);
        double stage2Provisions = calcStageTwoProvisions(arrears);
        this.stage1Provisions = stage1Provisions * 0.01;
        this.stage2Provisions = stage2Provisions * 0.03;

        writeLoans(arrears);
    }

    private void calcBadLoans(List<Messages.Arrears> arrears) {
        arrears.forEach(
                arrear -> {
                    if (arrear.monthsInArrears > 3) {
                        getLongAccumulator("badLoans").add(1);
                    }
                });
    }

    private void writeLoans(List<Messages.Arrears> arrears) {
        impairments = 0;
        arrears.forEach(
                arrear -> {
                    // A credit is written off if it is more than 24 months in arrears.
                    if (arrear.monthsInArrears > 24) {
                        impairments += arrear.outstandingBalance;

                        getLongAccumulator("writeOffs").add(1);

                        // Notify the sender their loan was defaulted.
                        send(Messages.LoanDefault.class).to(arrear.getSender());
                    }
                });
        assets -= impairments;
    }

    private int calcStageTwoProvisions(List<Messages.Arrears> arrears) {
        return arrears.stream()
                .filter(m -> m.monthsInArrears > 1 && m.monthsInArrears < 3)
                .mapToInt(m -> m.outstandingBalance)
                .sum();
    }

    private int calcStageOneProvisions(List<Messages.Arrears> arrears) {
        return arrears.stream()
                .filter(m -> m.monthsInArrears <= 1)
                .mapToInt(m -> m.outstandingBalance)
                .sum();
    }

    /** Remove any credits that have closed from the books. */
    void clearPaidCredits() {
        int balancePaidOff = 0;

        for (Messages.CloseCreditAmount closeAmount :
                getMessagesOfType(Messages.CloseCreditAmount.class)) {
            balancePaidOff += closeAmount.getBody();
            nbCredits -= 1;
        }

        debt -= balancePaidOff;
        assets -= balancePaidOff;
    }

    public void updateAccumulators() {
        getLongAccumulator("debt").add(debt);
        getLongAccumulator("impairments").add(impairments);
        getLongAccumulator("credits").add(nbCredits);
        getLongAccumulator("income").add(income);
        getLongAccumulator("assets").add(assets);
        getLongAccumulator("equity").add(equity());
    }

}