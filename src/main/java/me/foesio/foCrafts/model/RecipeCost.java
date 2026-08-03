package me.foesio.foCrafts.model;

public final class RecipeCost {
    private int xpLevels;
    private int xpPoints;
    private double vaultMoney;

    public RecipeCost() {
        this(0, 0, 0.0D);
    }

    public RecipeCost(int xpLevels, int xpPoints, double vaultMoney) {
        this.xpLevels = Math.max(0, xpLevels);
        this.xpPoints = Math.max(0, xpPoints);
        this.vaultMoney = Math.max(0.0D, vaultMoney);
    }

    public RecipeCost copy() {
        return new RecipeCost(xpLevels, xpPoints, vaultMoney);
    }

    public int getXpLevels() {
        return xpLevels;
    }

    public void setXpLevels(int xpLevels) {
        this.xpLevels = Math.max(0, xpLevels);
    }

    public int getXpPoints() {
        return xpPoints;
    }

    public void setXpPoints(int xpPoints) {
        this.xpPoints = Math.max(0, xpPoints);
    }

    public double getVaultMoney() {
        return vaultMoney;
    }

    public void setVaultMoney(double vaultMoney) {
        this.vaultMoney = Math.max(0.0D, vaultMoney);
    }

    public boolean hasAnyCost() {
        return xpLevels > 0 || xpPoints > 0 || vaultMoney > 0.0D;
    }
}
