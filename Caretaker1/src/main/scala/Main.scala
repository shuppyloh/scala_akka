/* OUTPUT OF PROGRAM
Alice says: i approve Bob's request to buy from Carol
Alice says: i create a caretaker for Bob 'ctbob_byAlice'
Alice says: i create a caretaker for Carol 'ctcarol_byAlice'
Alice says: i tell ctbob_byAlice that I approveTransact(ctcarol_byAlice) - I approve Bob to buy from Carol)
...(CARETAKER) ctbob_byAlice says: permission is true (owner: Alice)...forwarding Alice's msg to Bob
Bob says: i received approval from the exchange ctbob_byAlice to transact with ctcarol_byAlice
Bob says: i create a caretaker for myself 'ctbob'
Bob says: i create a caretaker for ctcarol_byAlice 'ctsellerExchange' and tell ctsellerExchange I want to buy(ctbob)
...(CARETAKER) ctsellerExchange says: permission is true (owner: Bob)...forwarding Bob's msg to ctcarol_byAlice
...(CARETAKER) ctcarol_byAlice says: permission is true (owner: Alice)...forwarding ctsellerExchange's msg to Carol
Carol says: i received ctbob's request to buy
Carol says: i create a caretaker for myself 'ctcarol'
Carol says: i create a caretaker for ctbob 'ctbuyer' and tell ctbuyer I want to sell(ctcarol)
...(CARETAKER) ctbuyer says: permission is true (owner: Carol)...forwarding Carol's msg to ctbob
...(CARETAKER) ctbob says: permission is true (owner: Bob)...forwarding ctbuyer's msg to Bob
Bob says: i received ctcarol's request to sell
Bob says: i tell ctcarol to complete transaction
Bob says: i create a caretaker for ctcarol 'ctseller' and tell ctseller to complete(ctbob)
...(CARETAKER) ctseller says: permission is true (owner: Bob)...forwarding Bob's msg to ctcarol
...(CARETAKER) ctcarol says: permission is true (owner: Carol)...forwarding ctseller's msg to Carol
Carol says: i received ctbob's request to complete transaction
Carol says: i tell ctbuyer to complete(ctcarol)
Carol says: transaction completed
Carol says: deleting caretakers 'ctbuyer', removing access of caretaker to myself
...(CARETAKER) ctbuyer says: permission is true (owner: Carol)...forwarding Carol's msg to ctbob
...(CARETAKER) ctbob says: permission is true (owner: Bob)...forwarding ctbuyer's msg to Bob
Bob says: i received ctcarol's request to complete transaction
Bob says: transaction completed
Bob says: deleting caretakers 'ctsellerExchange' and 'ctseller' , removing access of caretaker to myself
*/

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


case class buy(buyer:ActorRef);
case class sell(seller:ActorRef);
case class complete(from: ActorRef);
case class approveTransact(target:ActorRef);

class Caretaker(owner:ActorRef, target: ActorRef) extends Actor {
  var enabled = true;
  def receive = { 
    case "enable" => if(sender()==owner){enabled=true;}
    case "disable" => if(sender()==owner){enabled=false;}
    case msg => 
      print(s"...(CARETAKER) ${self.path.name} says: permission is $enabled (owner: ${owner.path.name})")
      if(enabled){
      println(s"...forwarding ${sender().path.name}'s msg to ${target.path.name}");
        target ! msg;
      }
  }
}
class Alice(bob: ActorRef, carol: ActorRef) extends Actor {
  val ctalice = context.actorOf(Props(classOf[Caretaker],self,self),"ctalice");
  def receive = {
    case "start" => 
      println(s"${self.path.name} says: i know both Bob and Carol (their actorRef)");
      println(s"${self.path.name} says: i approve Bob's request to buy from Carol");
      println(s"${self.path.name} says: i create a caretaker for Bob 'ctbob_byAlice'");
      println(s"${self.path.name} says: i create a caretaker for Carol 'ctcarol_byAlice'");
      println(s"${self.path.name} says: i tell ctbob_byAlice that I approveTransact(ctcarol_byAlice) - I approve Bob to buy from Carol)");
      val ctcarol = context.actorOf(Props(classOf[Caretaker],self,carol),"ctcarol_byAlice");
      val ctbob = context.actorOf(Props(classOf[Caretaker],self,bob),"ctbob_byAlice");
      ctbob ! approveTransact(ctcarol);
  }
}
class Bob extends Actor {
  var authSeller: ActorRef = null; //to mark who is the seller
  val ctbob = context.actorOf(Props(classOf[Caretaker],self,self),"ctbob");
  var ctsellerExchange:ActorRef = null;
  var ctseller:ActorRef = null;
  def receive = {
    case approveTransact(seller)=>
      println(s"${self.path.name} says: i received approval from the exchange ${sender().path.name} to transact with ${seller.path.name}");
      println(s"${self.path.name} says: i create a caretaker for myself 'ctbob'");
      println(s"${self.path.name} says: i create a caretaker for ${seller.path.name} 'ctsellerExchange' and tell ctsellerExchange I want to buy(ctbob)");
      //
      ctsellerExchange = context.actorOf(Props(classOf[Caretaker],self,seller),"ctsellerExchange");
      ctsellerExchange ! buy(ctbob);
    case sell(seller) => 
      println(s"${self.path.name} says: i received ${seller.path.name}'s request to sell");
      println(s"${self.path.name} says: i tell ${seller.path.name} to complete transaction");
      println(s"${self.path.name} says: i create a caretaker for ${seller.path.name} 'ctseller' and tell ctseller to complete(ctbob)")
      //
      authSeller = seller;
      ctseller= context.actorOf(Props(classOf[Caretaker],self,seller),"ctseller");
      ctseller ! complete(ctbob);
    case complete(seller) => 
      if(authSeller == seller){
        println(s"${self.path.name} says: i received ${seller.path.name}'s request to complete transaction");
        println(s"${self.path.name} says: transaction completed")
        println(s"${self.path.name} says: deleting caretakers 'ctsellerExchange' and 'ctseller' , removing access of caretaker to myself");
        //
        ctsellerExchange ! "disable"; ctsellerExchange = null;
        ctseller ! "disable"; ctseller = null;
        ctbob ! "disable"
        authSeller = null;
        context.system.terminate();
      }

  }
}
class Carol extends Actor {
  var authBuyer: ActorRef = null; //to mark who is the buyer 
  val ctcarol = context.actorOf(Props(classOf[Caretaker],self,self),"ctcarol");
  var ctbuyer:ActorRef = null;
  def receive = {
    case buy(buyer) => 
      println(s"${self.path.name} says: i received ${buyer.path.name}'s request to buy");
      println(s"${self.path.name} says: i create a caretaker for myself 'ctcarol'");
      println(s"${self.path.name} says: i create a caretaker for ${buyer.path.name} 'ctbuyer' and tell ctbuyer I want to sell(ctcarol)");
      //
      authBuyer = buyer
      ctbuyer = context.actorOf(Props(classOf[Caretaker],self,buyer),"ctbuyer");
      ctbuyer ! sell(ctcarol);
    case complete(buyer) => 
      if(authBuyer == buyer){
        println(s"${self.path.name} says: i received ${buyer.path.name}'s request to complete transaction");
        println(s"${self.path.name} says: i tell ctbuyer to complete(ctcarol)");
        println(s"${self.path.name} says: transaction completed");
        println(s"${self.path.name} says: deleting caretakers 'ctbuyer', removing access of caretaker to myself");
        //
        ctbuyer ! complete(ctcarol);
        ctbuyer ! "disable"; ctbuyer = null;
        ctcarol ! "disable"
        authBuyer = null;
      }
  }
}
object Main extends App {
  val system = ActorSystem("ocap");
  val bob = system.actorOf(Props[Bob],"Bob");
  val carol = system.actorOf(Props[Carol],"Carol");
  val alice = system.actorOf(Props(classOf[Alice],bob,carol),"Alice");
  //
  alice ! "start";
}
