package JavaApplication;
/*THE PROGRAM DEMONSTRATES THE COORDINATED USE OF REENTRANTLOCK AND CONDITION VARIABLES TO ENSURE THREAD SAFETY AND CONSISTENCY OF BANK ACCOUNT BALANCES DURING CONCURRENT DEPOSITS, 
WITHDRAWALS, AND TRANSFERS.*/


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class BankAccount {
    private final ReentrantLock lock = new ReentrantLock();
    // Threads wait on this when a withdrawal can't be covered; deposits signal it.
    private final Condition fundsAvailable = lock.newCondition();

    private final String accountName;
    private final long initialBalance;
    private long balance;

    // Only touched while holding the lock
    private final List<String> transactionLog = new ArrayList<>();
    private long totalDeposited = 0;
    private long totalWithdrawn = 0;

    public BankAccount(String accountName, long balance) {
        this.accountName = accountName;
        this.balance = balance;
        this.initialBalance = balance;
    }

    private void pause() { // simulates real-world delay
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

    // Must be called while holding the lock
    private void log(String entry) {
        transactionLog.add(entry);
    }

    public void deposit(long amount) {
        lock.lock();
        try {
            if (amount <= 0) {
                log("REJECTED DEPOSIT of " + amount + " (must be positive)");
                return;
            }
            long old = balance;
            pause();
            balance = old + amount;
            totalDeposited += amount;
            log("DEPOSIT " + amount + " | balance " + old + " -> " + balance);
            System.out.println("DEPOSITED " + amount + " INTO " + accountName + " | NEW BALANCE: " + balance);

            // Wake every waiting withdrawer so each can re-check the balance
            fundsAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits up to timeoutMs for enough funds. Returns true if the withdrawal happened,
     * false if it timed out (or the amount was invalid / the thread was interrupted).
     */
    public boolean withdraw(long amount, long timeoutMs) {
        lock.lock();
        try {
            if (amount <= 0) {
                log("REJECTED WITHDRAWAL of " + amount + " (must be positive)");
                return false;
            }
            long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            // while, not if: guards against spurious wakeups and another thread getting the funds first
            while (balance < amount) {
                if (nanos <= 0) {
                    log("TIMED OUT WITHDRAWAL of " + amount + " (balance " + balance + ")");
                    System.out.println("TIMED OUT waiting to withdraw " + amount + " from " + accountName);
                    return false;
                }
                try {
                    System.out.println("WAITING: withdrawal of " + amount + " from " + accountName
                            + " (balance " + balance + ")");
                    nanos = fundsAvailable.awaitNanos(nanos); // releases the lock while waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("INTERRUPTED WITHDRAWAL of " + amount);
                    return false;
                }
            }
            long old = balance;
            pause();
            balance = old - amount;
            totalWithdrawn += amount;
            log("WITHDRAWAL " + amount + " | balance " + old + " -> " + balance);
            System.out.println("WITHDREW " + amount + " FROM " + accountName + " | NEW BALANCE: " + balance);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean withdraw(long amount) {
        return withdraw(amount, 5000); // default: wait up to 5 seconds
    }

    /**
     * Withdraw from this account, then deposit into the target. Each step takes only ONE
     * account's lock at a time, so two opposite transfers (A->B and B->A) can never deadlock.
     */
    public boolean transfer(BankAccount target, long amount) {
        if (this.withdraw(amount)) {
            target.deposit(amount);
            System.out.println("TRANSFERRED " + amount + " FROM " + this.accountName + " TO " + target.accountName);
            return true;
        }
        System.out.println("FAILED TO TRANSFER " + amount + " FROM " + this.accountName + " TO " + target.accountName);
        return false;
    }

    public List<String> getTransactionLog() {
        lock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(transactionLog)); // snapshot
        } finally {
            lock.unlock();
        }
    }

    /** initial + successful deposits - successful withdrawals == current balance */
    public boolean verify() {
        lock.lock();
        try {
            long expected = initialBalance + totalDeposited - totalWithdrawn;
            System.out.println(accountName + ": " + initialBalance + " + " + totalDeposited + " - " + totalWithdrawn
                    + " = " + expected + " | actual = " + balance
                    + (expected == balance ? "  [OK]" : "  [MISMATCH]"));
            return expected == balance;
        } finally {
            lock.unlock();
        }
    }
}

// Thread 1: Customer A withdrawing
class A_Withdraw extends Thread {
    private final BankAccount a;
    private final long amount = 20000;
    A_Withdraw(BankAccount a) { this.a = a; }
    @Override public void run() { a.withdraw(amount); }
}

// Thread 2: Customer B depositing
class B_Deposit extends Thread {
    private final BankAccount b;
    private final long amount = 15000;
    B_Deposit(BankAccount b) { this.b = b; }
    @Override public void run() { b.deposit(amount); }
}

// Thread 3: Customer C transferring
class C_Transfer extends Thread {
    private final BankAccount from;
    private final BankAccount to;
    private final long amount = 30000;
    C_Transfer(BankAccount from, BankAccount to) { this.from = from; this.to = to; }
    @Override public void run() { from.transfer(to, amount); }
}

// Thread 4: Customer D withdrawing
class D_Withdraw extends Thread {
    private final BankAccount d;
    private final long amount = 25000;
    D_Withdraw(BankAccount d) { this.d = d; }
    @Override public void run() { d.withdraw(amount); }
}

// Thread 5: Customer E online purchase (a withdrawal)
class E_Withdraw extends Thread {
    private final BankAccount e;
    private final long amount = 10000;
    E_Withdraw(BankAccount e) { this.e = e; }
    @Override public void run() { e.withdraw(amount); }
}

// Thread 6: Customer F balance inquiry
class F_Inquiry extends Thread {
    private final BankAccount f;
    F_Inquiry(BankAccount f) { this.f = f; }
    @Override public void run() { System.out.println("Bank Balance: " + f.getBalance()); }
}

public class Coordination{
    public static void main(String[] args) {
        BankAccount shared = new BankAccount("B00", 100000);
        BankAccount other = new BankAccount("C00", 250000);

        Thread[] threads = {
                new A_Withdraw(shared),
                new B_Deposit(shared),
                new C_Transfer(shared, other),
                new D_Withdraw(shared),
                new E_Withdraw(shared),
                new F_Inquiry(shared)
        };

        for (Thread t : threads) t.start();

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n--- FINAL RESULTS ---");
        System.out.println("Final Balance of B00: " + shared.getBalance() + " (Expected: 30000)");
        System.out.println("Final Balance of C00: " + other.getBalance() + " (Expected: 280000)");

        System.out.println("\n--- VERIFICATION ---");
        boolean ok = shared.verify() & other.verify();
        System.out.println(ok ? "All balances consistent." : "INCONSISTENT BALANCES!");

        System.out.println("\n--- TRANSACTION LOG: " + shared.getAccountName() + " ---");
        shared.getTransactionLog().forEach(System.out::println);
        System.out.println("\n--- TRANSACTION LOG: " + other.getAccountName() + " ---");
        other.getTransactionLog().forEach(System.out::println);
    }
}