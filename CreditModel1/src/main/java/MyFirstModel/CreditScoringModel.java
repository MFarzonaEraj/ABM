package MyFirstModel;

import org.apache.commons.math3.random.EmpiricalDistribution;
import simudyne.core.abm.Action;
import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.annotations.Input;
import simudyne.core.annotations.ModelSettings;
import simudyne.core.data.CSVSource;

@ModelSettings(start = "2023-04-01T00:00:00Z",timeUnit="MONTHS",macroStep = 120)
public class CreditScoringModel extends AgentBasedModel<CreditScoringModel.Globals> {

    @Input(name = "Number of Households")
    public long nbHouseholds = 1000;
    private int wealth = 50000;

    public static final class Globals extends GlobalState {

        @Input(name = "Interest Rate (%)")
        public double interestRate = 20.0;

    }


    @Override
    public void init() {
        createLongAccumulator("equity", "Bank Equity (£)");
        createLongAccumulator("badLoans", "Bad Loans");
        createLongAccumulator("writeOffs", "Write-offs");
        createLongAccumulator("impairments", "Impairments (£k)");
        createLongAccumulator("debt", "Debt");
        createLongAccumulator("income", "Income");
        createLongAccumulator("credits", "Credits");
        createLongAccumulator("assets", "Assets");

        registerAgentTypes(Household.class, Bank.class);
        registerLinkTypes(Links.BankLink.class);
    }

    @Override
    public void setup() {
        EmpiricalDistribution incomeDist =
                getContext().getPrng().empiricalFromSource(new CSVSource("data/income-distribution.csv"));

        Group<Household> householdGroup =
                generateGroup(
                        Household.class,
                        nbHouseholds,
                        house -> {
                            house.income = (int) incomeDist.sample(1)[0];
                            house.wealth = wealth;
                        });

        Group<Bank> bankGroup = generateGroup(Bank.class, 1);

        householdGroup.partitionConnected(bankGroup, Links.BankLink.class);

        super.setup();
    }


    @Override
    public void step() {
        super.step();

        run(Household.applyForCredit, Bank.processApplication, Household.takeOutCredit);

        run(
                Action.create(
                        Household.class,
                        (Household h) -> {
                            h.earnIncome();
                            h.subsistenceConsumption();
                            h.payCredit();
                            h.discretionaryConsumption();
                        }),
                Action.create(
                        Bank.class,
                        (Bank b) -> {
                            b.accumulateIncome();
                            b.processArrears();
                            b.clearPaidCredits();
                            b.updateAccumulators();
                        }),
                Action.create(Household.class, h -> h.writeOff()));
    }
}
