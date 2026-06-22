package local.mmm.residencechunk.config;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigComments {

    private ConfigComments() {
    }

    public static void apply(FileConfiguration config) {
        config.options().parseComments(true);
        config.options().header("""
            MMMResidenceChunkBridge 功能配置
            语言文案已独立到 lang/zh_CN.yml；本文件只放可调功能参数。
            本文件的中文注释由插件代码维护，插件保存或重载配置时会自动补回。
            """);

        comment(config, "allowed-worlds",
            "允许玩家创建、扩建、调整领地的世界名列表。",
            "世界名必须和服务器实际世界文件夹 / Bukkit 世界名一致。",
            "为空时表示不限制世界。");

        comment(config, "currency", "Vault 金币显示配置。");
        comment(config, "currency.display-name",
            "Vault 金币显示名。",
            "这里只影响本插件提示，不会修改经济插件本身的货币名。");

        comment(config, "claims", "领地创建与形状规则。");
        comment(config, "claims.internal-name-prefix",
            "Residence 内部领地名前缀。",
            "实际内部名会附加玩家 UUID 片段和时间戳，避免重名。");
        comment(config, "claims.full-height",
            "是否创建从世界最低高度到最高高度的整列领地。",
            "true 表示玩家买的是完整区块柱。");
        comment(config, "claims.min-chunks",
            "单个领地最小区块数。",
            "缩小或重新选择边界时不能低于这个值。");
        comment(config, "claims.max-chunks-per-claim",
            "单个领地最大区块总数。",
            "创建、扩建、工具调整边界都会检查这个上限。");
        comment(config, "claims.no-claim-radius-blocks",
            "出生点/服务器中心保护半径，单位为方块。",
            "0 表示不启用中心保护。");
        comment(config, "claims.protected-center-x", "中心保护点 X 坐标。");
        comment(config, "claims.protected-center-z", "中心保护点 Z 坐标。");
        comment(config, "claims.rectangular-only",
            "是否强制领地必须是矩形。",
            "建议保持 true，避免玩家圈出不规则领地导致管理和计费混乱。");
        comment(config, "claims.set-teleport-on-create",
            "创建领地后是否把 Residence 传送点设置到玩家当前位置。");

        comment(config, "visual", "粒子预览配置。");
        comment(config, "visual.preview-duration-ticks",
            "普通领地边界预览持续时间，单位 tick，20 tick = 1 秒。");
        comment(config, "visual.preview-period-ticks",
            "普通领地边界预览刷新间隔，数值越小越明显，但粒子开销越高。");
        comment(config, "visual.preview-step-blocks", "普通边界每隔多少方块生成一组粒子。");
        comment(config, "visual.preview-corner-height", "普通边界四角向上显示的高度，单位方块。");
        comment(config, "visual.preview-dust-size", "普通边界红石尘粒子大小。");
        comment(config, "visual.selection", "玩家手动选择出来的矩形范围预览样式。");
        comment(config, "visual.selection.step-blocks", "选区边界每隔多少方块生成一组粒子。");
        comment(config, "visual.selection.corner-height", "选区四角向上显示的高度，单位方块。");
        comment(config, "visual.selection.dust-size", "选区红石尘粒子大小。");
        comment(config, "visual.selection.accent-enabled", "是否额外叠加发光感更强的强调粒子。");
        comment(config, "visual.selection.accent-particle",
            "选区强调粒子类型。",
            "常用：END_ROD、GLOW、HAPPY_VILLAGER。");
        comment(config, "visual.current-chunk-enabled", "是否显示玩家当前脚下区块边界。");
        comment(config, "visual.current-chunk", "玩家当前脚下区块预览样式。");
        comment(config, "visual.current-chunk.color",
            "当前区块颜色，格式为 \"红,绿,蓝\"，取值 0-255。");
        comment(config, "visual.current-chunk.step-blocks", "当前区块边界每隔多少方块生成一组粒子。");
        comment(config, "visual.current-chunk.corner-height", "当前区块四角向上显示的高度，单位方块。");
        comment(config, "visual.current-chunk.dust-size", "当前区块红石尘粒子大小。");
        comment(config, "visual.current-chunk.accent-enabled", "是否叠加强调粒子。");
        comment(config, "visual.current-chunk.accent-particle", "当前区块强调粒子类型。");

        comment(config, "selection", "手动圈地工具配置。");
        comment(config, "selection.tool",
            "用于左键/右键选择区块的物品材质名。",
            "必须是 Bukkit Material 名称，例如 GOLDEN_SHOVEL、WOODEN_HOE。");
        comment(config, "selection.require-tool", "是否要求玩家必须手持上面的工具才能选择区块。");
        comment(config, "selection.timeout-seconds", "选区会话超时时间，单位秒。");
        comment(config, "selection.preview-period-ticks", "选区预览刷新间隔，单位 tick。");

        comment(config, "teleport", "领地传送配置。");
        comment(config, "teleport.default-delay-seconds",
            "普通玩家传送等待时间，单位秒。",
            "0 表示普通玩家也立即传送。");
        comment(config, "teleport.permission-delays",
            "权限节点对应的传送等待时间，单位秒。",
            "玩家拥有多个节点时取最短时间。",
            "设置为 0 表示拥有该权限的玩家立即传送。");

        comment(config, "limits", "玩家领地数量限制。");
        comment(config, "limits.default-max-claims",
            "没有特殊权限时，玩家最多可拥有的本插件托管领地数量。");
        comment(config, "limits.permission-max-claims",
            "权限节点对应的最大领地数量。",
            "玩家拥有多个节点时取最大值。");

        comment(config, "pricing", "价格配置。");
        comment(config, "pricing.create", "创建新领地价格。");
        comment(config, "pricing.create.fallback-last-tier",
            "超过 tiers 中最大序号后，是否沿用最高档基础价格继续计算。");
        comment(config, "pricing.create.price-per-extra-chunk",
            "多区块创建时，每个额外区块增加的创建价格。",
            "例如创建 3 区块领地，会在基础创建价上额外加 2 次该价格。");
        comment(config, "pricing.create.tiers",
            "第 N 个领地的基础创建价格。",
            "key 是玩家的第几个领地，value 是价格。");
        comment(config, "pricing.expand", "扩建/工具调整边界新增区块价格。");
        comment(config, "pricing.expand.vault-max-chunks",
            "扩建后领地总区块数不超过该值时，使用 Vault 金币收费。",
            "超过该值时，使用 custom-currency 中配置的自管货币收费。");
        comment(config, "pricing.expand.progressive",
            "递增扩建价格。",
            "默认规则：第 1 个扩建区块 500，第 2 个 700，第 3 个 900。",
            "价格按领地当前面积累计计算，分多次扩建也不会重置回首块价格。");
        comment(config, "pricing.expand.progressive.base-price", "第一个扩建区块的价格。");
        comment(config, "pricing.expand.progressive.price-increase-per-chunk",
            "每新增一个扩建区块后，下一块比上一块增加的价格。");
        comment(config, "pricing.expand.custom-currency",
            "超过金币区块上限后使用的 MMMVaultSync 自管货币。");
        comment(config, "pricing.expand.custom-currency.enabled", "是否启用自管货币扩建。");
        comment(config, "pricing.expand.custom-currency.id", "MMMVaultSync 中注册的货币 ID。");
        comment(config, "pricing.expand.custom-currency.display-name", "自管货币显示名。");
        comment(config, "pricing.contract", "缩小领地价格策略。");
        comment(config, "pricing.contract.refund-enabled",
            "是否返还缩小减少的区块费用。",
            "当前代码逻辑保留该配置位，但默认不返还。");
    }

    private static void comment(FileConfiguration config, String path, String... lines) {
        config.setComments(path, List.of(lines));
    }
}
