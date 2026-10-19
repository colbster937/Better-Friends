package nya.blasted.mixin

import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(value = [PauseScreen::class], priority = 5000)
abstract class PauseScreenMixin(title: Component) : Screen(title) {

    @Unique
    private var `betterFriends$laidOutButtons`: List<AbstractWidget>? = null

    private fun isEssential(widget: AbstractWidget): Boolean {
        var cls: Class<*>? = widget.javaClass
        while (cls != null) {
            val name = cls.name.lowercase()
            if (name.contains("essential")) return true
            cls = cls.superclass
        }
        val msg = widget.message.string.lowercase()
        if (msg.contains("essential")) return true
        return false
    }

    @Inject(method = ["init"], at = [At("RETURN")])
    private fun modifyPauseMenu(ci: CallbackInfo) {
        val widgets = mutableListOf<AbstractWidget>()
        for (child in this.children()) {
            if (child is AbstractWidget) {
                widgets.add(child)
            }
        }

        val smallButtons = mutableListOf<AbstractWidget>()
        for (widget in widgets) {
            if (isEssential(widget)) continue
            if (widget.width <= 24) {
                smallButtons.add(widget)
            }
        }

        if (smallButtons.isNotEmpty()) {
            val vanillaCount = minOf(4, smallButtons.size)
            val vanillaSmallButtons = smallButtons.take(vanillaCount).toMutableList()
            val modButtons = smallButtons.drop(vanillaCount).toMutableList()

            // Find original row Y of vanilla small buttons
            val originalSmallRowY = vanillaSmallButtons.minOfOrNull { it.y } ?: Int.MAX_VALUE

            // Shift up buttons that were below the small buttons row to close the gap
            for (widget in widgets) {
                if (isEssential(widget)) continue
                if (!smallButtons.contains(widget) && widget.y > originalSmallRowY) {
                    widget.y -= 24
                }
            }

            // Always vertically center the pause menu button stack
            val menuWidgets = widgets.filter {
                !smallButtons.contains(it) &&
                !isEssential(it) &&
                it.y < this.height - 50 &&
                Math.abs((it.x + it.width / 2) - this.width / 2) < 80
            }

            if (menuWidgets.isNotEmpty()) {
                val minY = menuWidgets.minOf { it.y }
                val maxY = menuWidgets.maxOf { it.y + it.height }
                val totalMenuHeight = maxY - minY
                val targetTopY = (this.height - totalMenuHeight) / 2
                val verticalShift = targetTopY - minY

                for (widget in menuWidgets) {
                    widget.y += verticalShift
                }
                nya.blasted.BetterFriends.LOGGER.info("PauseScreenMixin: vertically centered pause menu (shifted by $verticalShift px, height=$totalMenuHeight)")
            }

            // Sort vanilla small buttons by original X coordinate to preserve natural left-to-right order (Report Bugs, Feedback, Friends, Player Reporting)
            for (i in 0 until vanillaSmallButtons.size - 1) {
                for (j in 0 until vanillaSmallButtons.size - i - 1) {
                    if (vanillaSmallButtons[j].x > vanillaSmallButtons[j + 1].x) {
                        val temp = vanillaSmallButtons[j]
                        vanillaSmallButtons[j] = vanillaSmallButtons[j + 1]
                        vanillaSmallButtons[j + 1] = temp
                    }
                }
            }

            // Mod buttons preserve their insertion order after vanilla buttons
            val orderedButtons = vanillaSmallButtons + modButtons

            val modSpacing = 4
            var totalWidth = 0
            for (btn in orderedButtons) {
                totalWidth += btn.width
            }
            totalWidth += (orderedButtons.size - 1) * modSpacing

            val startX = this.width / 2 - totalWidth / 2
            val newRowY = this.height - 32 // Anchored to the bottom of the screen

            var currentX = startX
            for (btn in orderedButtons) {
                val oldX = btn.x
                val oldY = btn.y
                btn.x = currentX
                btn.y = newRowY
                nya.blasted.BetterFriends.LOGGER.info("PauseScreen button [${btn.javaClass.simpleName}]: moved ($oldX, $oldY) -> (${btn.x}, ${btn.y}), size=${btn.width}x${btn.height}")
                currentX += btn.width + modSpacing
            }
            this.`betterFriends$laidOutButtons` = orderedButtons

            nya.blasted.BetterFriends.LOGGER.info("PauseScreenMixin: moved ${orderedButtons.size} 1x1 buttons (${vanillaSmallButtons.size} vanilla, ${modButtons.size} mod) to bottom of screen")
        }
    }

    @Inject(method = ["tick"], at = [At("RETURN")])
    private fun onTick(ci: CallbackInfo) {
        val buttons = this.`betterFriends$laidOutButtons` ?: return
        val newRowY = this.height - 32
        var needsRealign = false
        for (btn in buttons) {
            if (btn.y != newRowY) {
                needsRealign = true
                break
            }
        }
        if (needsRealign) {
            val modSpacing = 4
            var totalWidth = 0
            for (btn in buttons) {
                totalWidth += btn.width
            }
            totalWidth += (buttons.size - 1) * modSpacing

            val startX = this.width / 2 - totalWidth / 2
            var currentX = startX
            for (btn in buttons) {
                btn.x = currentX
                btn.y = newRowY
                currentX += btn.width + modSpacing
            }
            nya.blasted.BetterFriends.LOGGER.info("PauseScreenMixin: realigned ${buttons.size} 1x1 buttons in tick")
        }
    }
}
