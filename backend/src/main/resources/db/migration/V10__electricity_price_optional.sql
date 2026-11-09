-- Electricity expenses record hours (usage_quantity); the rupee price is OPTIONAL there, so expenses.amount may be 0.
-- Every other category still requires amount > 0 (enforced by ExpenseService, not by this constraint).
-- V1 declared `CHECK (amount > 0)` without a name, so look the generated name up instead of assuming it.
DO $$
DECLARE c TEXT;
BEGIN
    SELECT conname INTO c FROM pg_constraint
     WHERE conrelid = 'expenses'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) ~ 'amount > ';
    IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE expenses DROP CONSTRAINT %I', c);
    END IF;
END $$;

ALTER TABLE expenses ADD CONSTRAINT ck_exp_amount CHECK (amount >= 0);
