package MyFirstModel;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import simudyne.core.functions.SerializableConsumer;

public class Household extends Agent<CreditScoringModel.Globals> {

    @Variable public int income;
    @Variable public int wealth = 1000;

    @Variable
    public int repayment() {
        if (credit == null) {
            return 0;
        }

        return credit.repayment;
    }


    Credit credit;
    int monthsInArrears = 0;

    private static Action<Household> action(SerializableConsumer<Household> consumer) {
        return Action.create(Household.class, consumer);
    }

    void earnIncome() {
        wealth += income / 12;
    }

    void payCredit() {
        if (credit != null) {
            if (canPay()) {
                wealth -= credit.repayment;
                monthsInArrears = 0;
                credit.term -= 1;
                credit.balanceOutstanding -= credit.repayment;
                checkMaturity();
            } else {
                monthsInArrears += 1;
                getLinks(Links.BankLink.class)
                        .send(
                                Messages.Arrears.class,
                                (m, l) -> {
                                    m.monthsInArrears = monthsInArrears;
                                    m.outstandingBalance = credit.balanceOutstanding;
                                });
            }
        }
    }


        //проверить срок погашения
    private void checkMaturity() {
        if (credit.term == 0) {
            getLinks(Links.BankLink.class).send(Messages.CloseCreditAmount.class, credit.amount);
            credit = null;
        } else {
            getLinks(Links.BankLink.class)
                    .send(
                            Messages.Payment.class,
                            (m, l) -> {
                                m.repayment = credit.repayment;
                                m.amount = credit.amount;
                            });
        }
    }

    private Boolean canPay() {
        return wealth >= credit.repayment;
    }

    static Action<Household> applyForCredit =
            action(
                    h -> {
                        if (h.credit == null) {
                            if (h.getPrng().discrete(1, 5).sample() == 1) {
                                int purchasePrice = 10000 + h.income * 2;
                                h.getLinks(Links.BankLink.class)
                                        .send(
                                                Messages.CreditApplication.class,
                                                (m, l) -> {
                                                    m.amount = purchasePrice;
                                                    m.income = h.income;
                                                    m.wealth = h.wealth;
                                                });
                            }
                        }
                    });

    static Action<Household> takeOutCredit =
            action(
                    h ->
                            h.hasMessageOfType(
                                    Messages.ApplicationSuccessful.class,
                                    message ->
                                            h.credit =
                                                    new Credit(
                                                            message.amount,
                                                            message.amount,
                                                            message.termInMonths,
                                                            message.repayment)));




//Потребление средств к существованию
    void subsistenceConsumption() {
        wealth -= 5900 / 12;

        if (wealth < 0) {
            wealth = 1;
        }
    }
//дискреционное потребление
    void discretionaryConsumption() {
        //доход после прожиточного минимума
        int incomeAfterSubsistence = income - 5900;
        double minLiqWealth =
                4.07 * Math.log(incomeAfterSubsistence) - 33.1 + getPrng().gaussian(0, 1).sample();
        double monthlyConsumption = 0.5 * Math.max(wealth - Math.exp(minLiqWealth), 0);
        wealth -= monthlyConsumption;
    }

    void writeOff() {
        if (hasMessageOfType(Messages.LoanDefault.class)) {
            credit = null;
        }
    }

    public static class Credit {
        int amount;
        int balanceOutstanding;
        int term;
        int repayment;

        public Credit(int amount, int balanceOutstanding, int term, int repayment) {
            this.amount = amount;
            this.balanceOutstanding = balanceOutstanding;
            this.term = term;
            this.repayment = repayment;
        }
    }
}