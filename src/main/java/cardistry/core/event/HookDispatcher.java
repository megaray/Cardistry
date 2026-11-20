package cardistry.core.event;

import cardistry.core.EffectContext;

/** Traduit un {@link GameEvent} en appel de la méthode correspondante de {@link GameHooks}. */
public final class HookDispatcher {

    private HookDispatcher() {
    }

    public static void dispatch(GameHooks hook, GameEvent event, EffectContext ctx) {
        switch (event) {
            case GameEvent.GameStarted e -> hook.onGameStart(e, ctx);
            case GameEvent.CombatStarted e -> hook.onCombatStart(e, ctx);
            case GameEvent.HandDealt e -> hook.onHandDealt(e, ctx);
            case GameEvent.TurnStarted e -> hook.onTurnStart(e, ctx);
            case GameEvent.PlayerTurnStarted e -> hook.onPlayerTurnStart(e, ctx);
            case GameEvent.CardPlayed e -> hook.onCardPlayed(e, ctx);
            case GameEvent.CardReplayed e -> {
                // Concerne seulement l'UI.
            }
            case GameEvent.CardPowerTriggered e -> {
                // Concerne seulement l'UI.
            }
            case GameEvent.CardDrawn e -> hook.onCardDrawn(e, ctx);
            case GameEvent.ItemUsed e -> hook.onItemUsed(e, ctx);
            case GameEvent.CardChoiceOffered e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardChosen e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardReplaced e -> hook.onCardReplaced(e, ctx);
            case GameEvent.DamageDealt e -> hook.onDamageDealt(e, ctx);
            case GameEvent.SpellHit e -> hook.onSpellHit(e, ctx);
            case GameEvent.CriticalHit e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.FamiliarActed e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.FamiliarKnockedOut e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CombatStatChanged e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.BossBonus e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.StatPointSpent e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.AbilityTriggered e -> {
                // Concerne seulement l'UI.
            }
            case GameEvent.Healed e -> hook.onHeal(e, ctx);
            case GameEvent.TerrainChanged e -> hook.onTerrainChanged(e, ctx);
            case GameEvent.EnemyAttacked e -> hook.onEnemyAttack(e, ctx);
            case GameEvent.EnemyKilled e -> hook.onEnemyKilled(e, ctx);
            case GameEvent.GameWon e -> hook.onGameWon(e, ctx);
            case GameEvent.GameLost e -> hook.onGameLost(e, ctx);
            case GameEvent.DrunkennessChanged e -> hook.onDrunkennessChanged(e, ctx);
            case GameEvent.CurseGained e -> hook.onCurseGained(e, ctx);
            case GameEvent.PlayerTurnSkipped e -> hook.onTurnSkipped(e, ctx);
            case GameEvent.AttackCancelled e -> hook.onAttackCancelled(e, ctx);
            case GameEvent.EnemyTurnSkipped e -> hook.onEnemyTurnSkipped(e, ctx);
            case GameEvent.EffectAdded e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.EffectActivated e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.EffectEnded e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.StatusApplied e -> hook.onStatusApplied(e, ctx);
            case GameEvent.EnemyElementChanged e -> {
                // Concerne seulement l'UI.
            }
            case GameEvent.StatusRefused e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.StatusEnded e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CigaretteSmoked e -> hook.onCigaretteSmoked(e, ctx);
            case GameEvent.CigarettesChanged e -> hook.onCigarettesChanged(e, ctx);
            case GameEvent.CigaretteRolled e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CurseReduced e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CurseStarted e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CurseEnded e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.RewardOffered e -> hook.onRewardOffered(e, ctx);
            case GameEvent.RewardTaken e -> hook.onRewardTaken(e, ctx);
            case GameEvent.SetTierActivated e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.SetTierDeactivated e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.TrinketDiscarded e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.RewardSkipped e -> {
                // Rien à signaler aux trinkets.
            }
            case GameEvent.ActionRejected e -> {
                // Concerne seulement l'UI.
            }
            case GameEvent.EnemyNulled e -> {
                // Pas de hook pour l'instant (la mort passe par EnemyKilled).
            }
            case GameEvent.CardVoided e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.TurnUndone e -> {
                // Publié par run.Run, hors moteur : jamais livré aux trinkets.
            }
            case GameEvent.SecretUnlocked e -> {
                // Publié par run.UnlockTracker, hors moteur.
            }
            case GameEvent.IntentChosen e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.EnemyMoveUsed e -> {
                // Pas de hook pour l'instant (les coups passent par EnemyAttacked).
            }
            case GameEvent.EnemyMissed e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.SpellMissed e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.DodgeReady e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.EnemyDodged e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.EnemyHealed e -> {
                // Pas de hook pour l'instant (onHeal ne concerne que le joueur).
            }
            case GameEvent.CardCursed e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.ChoomiesStolen e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CigarettesStolen e -> {
                // Pas de hook pour l'instant (le paquet change : CigarettesChanged).
            }
            case GameEvent.TrinketStolen e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.TrinketReturned e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.StoryStarted e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.StoryResolved e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.ShopEntered e -> hook.onShopEnter(e, ctx);
            case GameEvent.ItemBought e -> hook.onItemBought(e, ctx);
            case GameEvent.ShopRestocked e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardRemovalBought e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.ShopLeft e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.DeckShuffled e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardRewardOffered e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardRewardSkipped e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardAddedToDeck e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.CardRemovedFromDeck e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.RestTaken e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.LevelStarted e -> {
                // Pas de hook pour l'instant.
            }
            case GameEvent.NodeEntered e -> {
                // Pas de hook pour l'instant (onShopEnter viendra avec le shop).
            }
            case GameEvent.ChoomiesChanged e -> {
                // Pas de hook pour l'instant.
            }
        }
    }
}
