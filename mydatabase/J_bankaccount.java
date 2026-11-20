/*
    FINALIZED BANK  TRANSACTION PROGRAM
*/
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class BankAccount {
    private static final long WAIT_MS = 5000;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition fundsAvailable = lock.newCondition();
    private final String accountName;
    private final long initialBalance;
    private long balance;
    private final List<String> transactionLog = new ArrayList<>();

    public BankAccount(String accountName, long balance) {
        this.accountName = accountName;
        this.balance = balance;
        this.initialBalance = balance;
    }

    private void pause() { // simulates a real-world processing delay
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getAccountName() {
        return accountName;
    }

    public long getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    private void log(String entry) {
        transactionLog.add("[" + Thread.currentThread().getName() + "] " + entry);
    }

    public long inquireBalance(String who) {
        lock.lock();
        try {
            System.out.println(who + ": BALANCE INQUIRY on " + accountName + " = " + balance);
            return balance;
        } finally {
            lock.unlock();
        }
    }


    public void deposit(long amount) {
        deposit(amount, "DEPOSIT", true , true);
    }

    private void deposit(long amount, String label, boolean print, boolean persist) {
        lock.lock();
        try {
            if (amount <= 0) {
                log("REJECTED " + label + " of " + amount + " (must be positive)");
                if (print) {
                    System.out.println("REJECTED: deposit amount must be positive.");
                }
                return;
            }
            long old = balance;
            pause();
            balance = old + amount;

            if(persist){
                try{
                    BankDatabase.recordTransaction(accountName, Thread.currentThread().getName(), label, amount, old, balance);
                } catch (SQLException e) {
                    balance=old;
                    log("FAILED " + label + " of " + amount + " (database error)");
                    System.out.println("DATABASE ERROR: " + e.getMessage());
                    return;
                }
            }
            log(label + " " + amount + " | balance " + old + " -> " + balance);
            if (print) {
                System.out.println(Thread.currentThread().getName() + ": DEPOSITED " + amount
                        + " INTO " + accountName + " | NEW BALANCE: " + balance);
            }
            // Wake every waiting withdrawal so each can re-check the balance
            fundsAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }


    public boolean withdraw(long amount) {
        return withdraw(amount, WAIT_MS, "WITHDRAWAL", true, true);
    }

    private boolean withdraw(long amount, long timeoutMs, String label, boolean print, boolean persist) {
        lock.lock();
        try {
            if (amount <= 0) {
                log("REJECTED " + label + " of " + amount + " (must be positive)");
                if (print) {
                    System.out.println("REJECTED: withdrawal amount must be positive.");
                }
                return false;
            }
            long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (balance < amount) {
                if (nanos <= 0) {
                    log("TIMED OUT " + label + " of " + amount + " (balance " + balance + ")");
                    System.out.println(Thread.currentThread().getName() + ": TIMED OUT waiting to withdraw "
                            + amount + " from " + accountName);
                    return false;
                }
                try {
                    nanos = fundsAvailable.awaitNanos(nanos); // releases the lock while waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("INTERRUPTED " + label + " of " + amount);
                    return false;
                }
            }
            long old = balance;
            pause();
            balance = old - amount;
            if(persist){
                try{
                    BankDatabase.recordTransaction(accountName, Thread.currentThread().getName(), label, amount, old, balance);
                }catch (SQLException e){
                    balance=old;
                    log("FAILED " + label + " of " + amount + " (Database Error)");
                    System.out.println("DATABASE ERROR: " + e.getMessage());
                    return false;
                }
            }
            log(label + " " + amount + " | balance " + old + " -> " + balance);
            if (print) {
                System.out.println(Thread.currentThread().getName() + ": WITHDREW " + amount
                        + " FROM " + accountName + " | NEW BALANCE: " + balance);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean transfer(BankAccount target, long amount) {
        String threadName = Thread.currentThread().getName();
        if (this.withdraw(amount, WAIT_MS, "TRANSFER OUT to " + target.accountName, false,false)) {
            target.deposit(amount, "TRANSFER IN from " + this.accountName, false,false);
            System.out.println(threadName + ": TRANSFERRED " + amount + " FROM "
                    + this.accountName + " TO " + target.accountName);
            return true;
        }
        System.out.println(threadName + ": FAILED TO TRANSFER " + amount + " FROM "
                + this.accountName + " TO " + target.accountName);
        return false;
    }

    // transaction log

    public void printTransactionLog() {
        lock.lock();
        try {
            System.out.println("Account " + accountName + " (opening balance " + initialBalance + ")");
            if (transactionLog.isEmpty()) {
                System.out.println("  (no transactions)");
            }
            int n = 1;
            for (String entry : transactionLog) {
                System.out.println("  " + n++ + ". " + entry);
            }
            System.out.println("  Closing balance: " + balance);
            System.out.println();
        } finally {
            lock.unlock();
        }
    }
}

// Thread 1: Customer A withdrawing
class A_Withdraw extends Thread {
    private final BankAccount a;
    private final long amount = 20000;

    A_Withdraw(BankAccount a) {
        super("Customer-A");
        this.a = a;
    }

    @Override
    public void run() {
        a.withdraw(amount);
    }
}

// Thread 2: Customer B depositing
class B_Deposit extends Thread {
    private final BankAccount b;
    private final long amount = 15000;

    B_Deposit(BankAccount b) {
        super("Customer-B");
        this.b = b;
    }

    @Override
    public void run() {
        b.deposit(amount);
    }
}

// Thread 3: Customer C transferring
class C_Transfer extends Thread {
    private final BankAccount from;
    private final BankAccount to;
    private final long amount = 30000;

    C_Transfer(BankAccount from, BankAccount to) {
        super("Customer-C");
        this.from = from;
        this.to = to;
    }

    @Override
    public void run() {
        from.transfer(to, amount);
    }
}

// Thread 4: Customer D withdrawing
class D_Withdraw extends Thread {
    private final BankAccount d;
    private final long amount = 25000;

    D_Withdraw(BankAccount d) {
        super("Customer-D");
        this.d = d;
    }

    @Override
    public void run() {
        d.withdraw(amount);
    }
}

// Thread 5: Customer E online purchase (a withdrawal)
class E_Withdraw extends Thread {
    private final BankAccount e;
    private final long amount = 10000;

    E_Withdraw(BankAccount e) {
        super("Customer-E");
        this.e = e;
    }

    @Override
    public void run() {
        e.withdraw(amount);
    }
}

// Thread 6: Customer F balance inquiry
class F_Inquiry extends Thread {
    private final BankAccount f;

    F_Inquiry(BankAccount f) {
        super("Customer-F");
        this.f = f;
    }

    @Override
    public void run() {
        f.inquireBalance(getName());
    }
}

public class J_bankaccount {

    private static void section(String title) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(title);
        System.out.println("==================================================");
    }

    public static void main(String[] args) {

        BankAccount shared;
        BankAccount other;

        try{
            BankDatabase.resetDemoData();   //clears old transactions
            shared= new BankAccount("B00", BankDatabase.loadBalance("B00"));
            other=  new BankAccount("C00", BankDatabase.loadBalance("C00"));

        }catch (SQLException e){
            System.out.println("Could not set up the database: " + e.getMessage());
            return;     //no point running without without the database
        }

        Thread[] threads = {
                new A_Withdraw(shared),
                new B_Deposit(shared),
                new C_Transfer(shared, other),
                new D_Withdraw(shared),
                new E_Withdraw(shared),
                new F_Inquiry(shared)
        };

        section("LIVE ACTIVITY");
        for (Thread t : threads) {
            t.start();
        }

        // Wait for ALL threads to finish before reading any final result
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        section("TRANSACTION LOGS");
        shared.printTransactionLog();
        other.printTransactionLog();

        section("FINAL BALANCES");
        System.out.println("Final Balance of B00: " + shared.getBalance() + " (Expected: 30000)");
        System.out.println("Final Balance of C00: " + other.getBalance() + " (Expected: 280000)");
    }
}