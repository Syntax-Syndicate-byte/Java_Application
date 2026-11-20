package JavaApplication;
class BankAccount {
    private final String accountName;
    private long balance;

    public BankAccount(String accountName, long balance) {
        this.accountName = accountName;
        this.balance = balance;
    }

    public String getAccountName() {
        return accountName;
    }

    public long getBalance() {
        return balance;
    }
    public  void deposit(long amount){
        deposit(amount, true);
    }
    public void deposit(long amount, boolean print) {
        if (amount > 0) {
            balance += amount;
            if(print){
                System.out.println("DEPOSITED " + amount + " INTO " + accountName + " | NEW BALANCE :" + balance + " \n");
            }
        } else {
            System.out.println("Deposit amount must be positive.");
        }
    }

    public boolean withdraw(long amount) {
        return withdraw(amount, true);
    }

    public boolean withdraw(long amount, boolean print) {
        if (amount > 0 && amount <= balance) {
            balance -= amount;
            if(print){
                System.out.println("WITHDREW " + amount + " FROM " + accountName + " | NEW BALANCE : " + balance + " \n");
            }
            return true;
        } else {
            if(print){
                System.out.println("UNSUCCESSFUL WITHDRAWAL OF " + amount + " FROM " + accountName + " | PLEASE CHECK YOUR BALANCE!! \n");
            }
            return false;
        }
    }

    public  void transfer(BankAccount target, long amount) { 
        if (this.withdraw(amount , false)) {
            target.deposit(amount, false);
            System.out.println("TRANSFERRED AN AMOUNT OF " + amount + " FROM THE ACCOUNT " + this.accountName + " TO THE ACCOUNT " + target.accountName + " | NEW BALANCE : " + this.balance + " \n");
        } else {
            System.out.println("FAILED TO TRANSFER " + amount + " FROM THE ACCOUNT " + this.accountName + " TO THE ACCOUNT " + target.accountName + " PLEASE CHECK ACCOUNT BALANCE!! \n");
        }
    }
}

//Thread 1: Customer A Withdrawing
class A_Withdraw extends Thread {
    private final BankAccount a;
    private final long amount = 20000;

    A_Withdraw(BankAccount a) {
        this.a = a;
    }
    @Override 
    public void run() {
        a.withdraw(amount,true);
    }
}

//Thread 2: Customer B depositing
class B_Deposit extends Thread {
    private final BankAccount b;
    private final long amount = 15000;

    B_Deposit(BankAccount b) {
        this.b = b;
    }

    @Override
    public void run() {
        b.deposit(amount,true);
    }
}

//Thread 3: Customer C Transferring
class C_Transfer extends Thread {
    private final BankAccount c;
    private final BankAccount c1;
    private final long amount = 30000;

    C_Transfer(BankAccount c, BankAccount c1) {
        this.c = c;
        this.c1 = c1;
    }
    @Override
    public void run() {
        c.transfer(c1, amount);
    }
}

//Thread 4: Customer D withdrawing
class D_Withdraw extends Thread {
    private final BankAccount d;
    private final long amount = 25000;

    D_Withdraw(BankAccount d) {
        this.d = d;
    }

    @Override
    public void run() {
        d.withdraw(amount,true);
    }
}

//Thread 5:Customer E making an online purchase seen as a Withdrawal
class E_Withdraw extends Thread {
    private final BankAccount e;
    private final long amount = 10000;

    E_Withdraw(BankAccount e) {
        this.e = e;
    }
    @Override
    public void run() {
        e.withdraw(amount,true);
    }
}

//Thread 6: Customer F Checking Balance Inquiry
class F_Inquiry extends Thread {
    private final BankAccount f;

    F_Inquiry(BankAccount f) {
        this.f = f;
    }
    @Override 
    public void run() {
        System.out.println("Bank Balance : " + f.getBalance());
    }
}

public class javaBankAccount{
    public static void main(String[] args){
        BankAccount shared= new BankAccount("B00", 100000);
        BankAccount other=new BankAccount("C00", 250000);

        Thread[] thread={
                new A_Withdraw(shared),
                new B_Deposit(shared),
                new C_Transfer(shared, other),
                new D_Withdraw(shared),
                new E_Withdraw(shared),
                new F_Inquiry(shared)
        };

        for(Thread t: thread){
            t.start();
        }

        for(Thread t: thread){
            try{
                t.join();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
        System.out.println("Final Balance of B00: " + shared.getBalance() + " (Expected balance is :30000)");
        System.out.println("Final Balance of C00: " + other.getBalance() + " (Expected Balance is :280000)");

    }
}