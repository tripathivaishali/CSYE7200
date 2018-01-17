package edu.neu.coe.csye7200.hedge_fund.actors

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps
import edu.neu.coe.csye7200.hedge_fund.model.Model
import edu.neu.coe.csye7200.hedge_fund.portfolio.{Contract, Portfolio, Position}

/**
 * CONSIDER renaming this as PortfolioManager
 *
 * @author robinhillyard
 */
class UpdateLogger(blackboard: ActorRef) extends BlackboardActor(blackboard) {

  var portfolio = new Portfolio("", Nil)

  override def receive =
    {
      case Confirmation(id, model, attrs) =>
        log.debug(s"update for identifier: $id")
        if (model.isOption)
          processOption(id, model, attrs)
        else
          processStock(id, model)

      case PortfolioUpdate(p) =>
        log.debug(s"portfolio update for: ${p.name}")
        portfolio = p
        showPortfolio

      case m => super.receive(m)
    }

  implicit val timeout = Timeout(5 seconds)

  def processStock(identifier: String, model: Model) = {
    model.getKey("price") match {
      case Some(p) => {
        // sender is the MarketData actor
        val future = sender ? SymbolQuery(identifier, List(p))
        val result = Await.result(future, timeout.duration).asInstanceOf[QueryResponse]
        result.attributes map {
          case (k, v) => log.info(s"$identifier attribute $k has been updated to: $v")
        }
      }
      case None => log.warning(s"'price' not defined in model")
    }
  }

  def processOption(identifier: String, model: Model, attributes: Map[String, Any]) = {
    val key = "underlying"
    attributes.get(key) match {
      case Some(value) =>
        val future = blackboard ? OptionQuery("id", value)
        val result = Await.result(future, timeout.duration).asInstanceOf[QueryResponse]
        println(s"Action Required: re: qualifying option $identifier with underlying symbol: ${result.identifier} and attributes: ${result.attributes}")
      case None => log.warning(s"processOption: value not present for $key")
    }
  }

  def showPortfolio {
    println(s"Portfolio for ${portfolio.name}")
    portfolio.positions foreach { showPosition(_) }
  }

  def showPosition(position: Position) {
    println(s"position for ${position.symbol}: quantity=${position.quantity}; options=")
    position.contracts foreach { showContract(_) }
  }

  def showContract(contract: Contract) {
    println(s"contract: $contract")
  }
}
