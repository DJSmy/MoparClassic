package org.moparscape.msc.gs.event.handler.objectaction.impl
import java.lang.{ IllegalArgumentException => IAE }

import org.moparscape.msc.gs.config.Formulae
import org.moparscape.msc.gs.event.handler.objectaction.ObjectEvent
import org.moparscape.msc.gs.event.ShortEvent
import org.moparscape.msc.gs.model.definition.skill.ObjectFishingDef
import org.moparscape.msc.gs.model.definition.EntityHandler
import org.moparscape.msc.gs.model.Bubble
import org.moparscape.msc.gs.service.ItemAttributes
import org.moparscape.msc.gs.Instance


class Fishing extends ObjectEvent {

	private val FISHING = 10

	override def fire : Boolean = {
		val fishingDef = EntityHandler.getObjectFishingDef(o.getID, click)
		if (fishingDef == null) {
			true
		} else {
			if (player.getCurStat(10) < fishingDef.getReqLevel())
				player.getActionSender.sendMessage("You need a fishing level of " + fishingDef.getReqLevel + " to fish here.")
			else try { fish(fishingDef) } catch { case e : IAE => player.getActionSender.sendMessage(e.getMessage) }
			false
		}
	}

	private def fish(d : ObjectFishingDef) {
		if (!player.withinRange(o, 1) || player.isBusy) {
			return
		}

		val toolId = d.getNetId

		if (!player.getInventory.contains(toolId)) {
			throw new IAE("You need a " + ItemAttributes.getItemName(toolId) + " to catch these fish.")
		}

		val baitId = d.getBaitId
		if (baitId > 0 && !player.getInventory.contains(baitId)) {
			throw new IAE("You don't have any " + ItemAttributes.getItemName(baitId) + " to catch these fish.")
		}

		player.setBusy(true)
		player.getActionSender.sendSound("fish")

		new Bubble(player, toolId).broadcast

		player.getActionSender.sendMessage("You attempt to catch some fish")


		Instance.getDelayedEventHandler.add(
			new ShortEvent(player) {
				override def action {
					try {
						val fishDef = Formulae.getFish(o.getID, owner.getCurStat(10), click)
						if (fishDef == null) {
							owner.getActionSender.sendMessage("You fail to catch anything.")
						} else {

							owner.getActionSender.sendMessage("You catch a " + ItemAttributes.getItemName(fishDef.getId) + ".")

							val i = owner.getInventory
							i.doThenSend {
								i.remove(baitId)
								i.add(fishDef.getId)

							}

							owner.incExp(FISHING, fishDef.getExp, true)
							owner.getActionSender.sendStat(FISHING)
						}
					} finally {
						owner.setBusy(false)
					}
				}




			})
	}
}