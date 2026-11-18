package cardistry.items;

/** Rareté d'un objet ou trinket : poids de tirage et prix indicatif (voir GAME_DESIGN.md). */
public enum Rarity {
    COMMON("Commun", 60, 40, 50),
    UNCOMMON("Peu commun", 25, 30, 80),
    RARE("Rare", 12, 20, 130),
    LEGENDARY("Légendaire", 5, 3, 200),
    /** N'apparaît jamais dans les tirages normaux. */
    SECRET("Secret", 0, 0, 0);


    private final String displayName;
    private final int dropWeight;
    private final int price;
    private final int cardWeight;

    Rarity(String displayName, int dropWeight, int cardWeight, int price) {
        this.displayName = displayName;
        this.dropWeight = dropWeight;
        this.cardWeight=cardWeight;
        this.price = price;
    }

    public String displayName() {
        return displayName;
    }

    /** Poids relatif dans les tirages de récompense (0 = jamais tiré). */
    public int dropWeight() {
        return dropWeight;
    }

    public int cardWeight(){
        return cardWeight;
    }

    public int price() {
        return price;
    }
}
