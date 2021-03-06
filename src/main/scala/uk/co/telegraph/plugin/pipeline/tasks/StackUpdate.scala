package uk.co.telegraph.plugin.pipeline.tasks

import sbt.{File, Logger, URI, uri}
import uk.co.telegraph.cloud.dls.{await, update}
import uk.co.telegraph.cloud.{StackConfig, StackName, StackRegion}
import uk.co.telegraph.plugin.pipeline._

import scala.util.{Failure, Success, Try}

trait StackUpdate extends GenericTask{

  val taskName = "Update"

  def apply(
    name:StackName,
    region:StackRegion,
    auth:StackAuth,
    capabilities:Seq[String],
    tags:StackTags,
    paramsPath:File,
    paramsCustom:StackParams,
    templateS3Uri:URI,
    templateFormat:StackTemplateFormat
  )(implicit environment:String, logger:Logger):Unit = {
    val templateUri = uri(s"${templateS3Uri.toString}/template.${templateFormat.format}")
    // Compute the Parameters
    val parameters  = doReadParameters(paramsPath) ++ paramsCustom
    val config      = StackConfig(capabilities, templateUri, tags, parameters)

    logDetails(name, region, auth, Some(config))
    Try{
      update(name, config)
        .flatMap( _ => await(name) )
        .foldMap(interpreter(region, auth))
    } match {
      case Success(stackStatus) =>
        stackStatus.orElse(Some("UNKNOWN")) match {
          case Some("UPDATE_COMPLETE") =>
            logger.info("Stack Updated Successfully")
          case Some(status) =>
            sys.error(s"ERROR: Stack Update failed - '$status'")
          case _ =>
        }
      case Failure(ex) =>
        sys.error(s"ERROR: Fail during 'stackUpdate' - ${ex.getMessage}")
    }
  }
}

object StackUpdate extends StackUpdate
