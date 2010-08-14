/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import std._
import Path._
import TaskExtra._
import scala.collection.{mutable, JavaConversions}

import java.io.File

trait SingleProject extends Tasked
{
	def base: File
	def streamBase = base / "streams"

	type Task[T] = sbt.Task[T]
	def act(input: Input, state: State): Option[(Task[State], Execute.NodeView[Task])] =
	{
		import Dummy._
		val context = ReflectiveContext(this)
		val dummies = new Transform.Dummies(In, State, Streams)
		val injected = new Transform.Injected( input, state, std.Streams(t => streamBase / std.Streams.name(t)) )
		context.forName(input.name) map { t => (t map(_ => state), Transform(dummies, injected, context) ) }
	}

	def help: Seq[Help] = Nil
}
object Dummy
{
	val InName = "command-line-input"
	val StateName = "command-state"
	val StreamsName = "task-streams"
	
	def dummy[T](name: String): Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") )  named name
	val In = dummy[Input](InName)
	val State = dummy[State](StateName)
	val Streams = dummy[TaskStreams](StreamsName)
}

object ReflectiveContext
{
	import Transform.Context
	def apply[Owner <: AnyRef : Manifest](context: Owner): Context[Owner] = new Context[Owner]
	{
		private[sbt] lazy val tasks: Map[String, Task[_]] = ReflectUtilities.allVals[Task[_]](context).toMap.transform { case (nme,task) => setName(task,nme) }
		private[sbt] lazy val reverseName: collection.Map[Task[_], String] = reverseMap(tasks)
		private[sbt] lazy val sub: collection.Map[String, Owner] = ReflectUtilities.allVals[Owner](context)
		private[sbt] lazy val reverseSub: collection.Map[Owner, String] = reverseMap(sub)

		def forName(s: String): Option[Task[_]] = tasks get s
		def staticName: Task[_] => Option[String] = reverseName.get _
		def owner = (_: Task[_]) => Some(context)
		def subs = (o: Owner) => Nil
		def static = (o: Owner, s: String) => if(o eq context) tasks.get(s) else None

		private def reverseMap[A,B](in: Iterable[(A,B)]): collection.Map[B,A] =
		{
			import JavaConversions._
			val map: mutable.Map[B, A] = new java.util.IdentityHashMap[B, A]
			for( (name, task) <- in ) map(task) = name
			map
		}
		private def setName[T](task: Task[T], nme: String): Task[T] =
			task.copy(task.info.copy(name = task.info.name orElse Some(nme), original = Some(task) ))
	}
}