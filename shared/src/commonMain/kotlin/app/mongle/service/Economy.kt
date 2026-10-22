package app.mongle.service

import app.mongle.logic.Reward
import app.mongle.logic.RewardPolicy
import app.mongle.model.GameBalance
import app.mongle.model.Stage

/**
 * 보상·구매의 권한 경계.
 * MVP는 [LocalEconomyAuthority]가 기기에서 규칙을 적용한다.
 * 커뮤니티 단계에서는 같은 인터페이스의 서버 구현이 원장에 기록하고 거절할 수 있다.
 */
interface EconomyAuthority {
    fun grantCompletion(request: CompletionRequest): GrantDecision
    fun grantPurchase(request: PurchaseRequest): GrantDecision
}

data class CompletionRequest(
    val createdAt: Long,
    val scheduledAt: Long,
    val now: Long,
    val stage: Stage,
    val rewardedToday: Int,
)

data class PurchaseRequest(
    val defId: String,
    val price: Int,
    val coins: Int,
    val alreadyOwned: Boolean,
    val stackable: Boolean,
)

sealed interface GrantDecision {
    data class Allow(val xp: Int, val coins: Int, val happiness: Int) : GrantDecision
    data class Deny(val message: String) : GrantDecision
}

class LocalEconomyAuthority(
    private val balance: GameBalance = GameBalance(),
) : EconomyAuthority {
    override fun grantCompletion(request: CompletionRequest): GrantDecision {
        val reward: Reward = RewardPolicy.compute(
            stage = request.stage,
            createdAt = request.createdAt,
            scheduledAt = request.scheduledAt,
            now = request.now,
            rewardedToday = request.rewardedToday,
            balance = balance,
        )
        return GrantDecision.Allow(reward.xp, reward.coins, reward.happiness)
    }

    override fun grantPurchase(request: PurchaseRequest): GrantDecision {
        if (request.alreadyOwned && !request.stackable) {
            return GrantDecision.Deny("이미 가지고 있어요")
        }
        if (request.coins < request.price) {
            return GrantDecision.Deny(app.mongle.l10n.L10n.t("msg_deny_coins"))
        }
        return GrantDecision.Allow(xp = 0, coins = request.price, happiness = 0)
    }
}

/**
 * 산책 월드, 채팅, 유저 간 거래. 이번 단계에서는 구현하지 않고 호출부만 남긴다.
 */
interface CommunityRepository {
    suspend fun fetchNearby(): Result<List<RemotePet>>
    suspend fun sendQuickChat(presetId: String): Result<Unit>
    suspend fun proposeTrade(draft: TradeDraft): Result<String>
}

data class RemotePet(
    val petId: String,
    val displayName: String,
    val stage: Stage,
    val outfitId: String?,
)

data class TradeDraft(
    val offerItemIds: List<String>,
    val offerCoins: Int,
    val requestItemIds: List<String>,
    val requestCoins: Int,
)

class UnavailableCommunityRepository : CommunityRepository {
    private fun <T> closed(): Result<T> =
        Result.failure(UnsupportedOperationException("커뮤니티 월드는 아직 연결되지 않았어요"))

    override suspend fun fetchNearby(): Result<List<RemotePet>> = closed()
    override suspend fun sendQuickChat(presetId: String): Result<Unit> = closed()
    override suspend fun proposeTrade(draft: TradeDraft): Result<String> = closed()
}
