import play.api._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

}
/*object Global {

  // Handle the trailing "/"
  override def onRouteRequest(request: RequestHeader) = super.onRouteRequest(request).orElse {
    Option(request.path).filter(_.endsWith("/"))
      .flatMap( p => super.onRouteRequest(
        request.copy(path = p.dropRight(1),
          uri = p.dropRight(1) +  request.uri.stripPrefix(p))
        )
      )
  }
}*/