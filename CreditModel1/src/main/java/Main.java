package MyFirstModel;


import simudyne.nexus.Server;


public class Main {
  public static void main(String[] args) {

    Server.register("Credit Scoring Model", CreditScoringModel.class);

    Server.run(args);
  }
}
