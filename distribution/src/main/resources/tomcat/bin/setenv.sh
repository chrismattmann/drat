#!/bin/sh

# Keep direct Tomcat starts aligned with the DRAT/OODT launcher.
if [ -z "$DRAT_HOME" ] && [ -n "$CATALINA_BASE" ]; then
  DRAT_HOME=`cd "$CATALINA_BASE/.." 2>/dev/null && pwd`
fi

if [ -n "$DRAT_HOME" ]; then
  # Solr 4.2 in DRAT's webapp also reads solr/home as ../solr, so keep
  # direct catalina.sh starts in the same working directory as bin/oodt.
  if [ -d "$DRAT_HOME/bin" ]; then
    cd "$DRAT_HOME/bin"
  fi

  # The deployment's own settings, which is where its ports and service urls
  # are already written down. Without them the webapps fall back to defaults
  # that belong to whatever else is on this machine: the health panel read
  # another deployment's workflow manager on 8080 and reported its jobs as
  # DRAT's, and the charts asked a Solr on 8983 that has no drat core. The
  # webapps resolve [FILEMGR_URL] and its siblings from the environment as
  # they load, so a Tomcat started without these serves 500s from every
  # service in the pcs webapp.
  if [ -r "$DRAT_HOME/bin/setenv.sh" ]; then
    . "$DRAT_HOME/bin/setenv.sh"
  fi

  # Named for what the webapps look for. These are this deployment's own
  # Tomcat and Solr, not a default that happens to be free.
  if [ -z "$DRAT_BASE_URL" ] && [ -n "$TOMCAT_PORT" ]; then
    DRAT_BASE_URL="http://${OODT_HOST:-localhost}:$TOMCAT_PORT"
  fi
  if [ -z "$DRAT_SOLR_BASE_URL" ] && [ -n "$SOLR_PORT" ]; then
    DRAT_SOLR_BASE_URL="http://${OODT_HOST:-localhost}:$SOLR_PORT"
  fi
  export DRAT_BASE_URL DRAT_SOLR_BASE_URL

  case " $CATALINA_OPTS " in
    *" -Dsolr.solr.home="*) ;;
    *) CATALINA_OPTS="$CATALINA_OPTS -Dsolr.solr.home=$DRAT_HOME/solr" ;;
  esac
  export DRAT_HOME CATALINA_OPTS
fi

# CXF's JAX-RS client reflects into java.net.Authenticator, which the module
# system has refused since Java 17. Without this the services that call out to
# Solr and the health monitor fail with InaccessibleObjectException, which
# surfaces as HTTP 500 from /proteus-services/service/repo/breakdown/mime.
case " $CATALINA_OPTS " in
  *" --add-opens=java.base/java.net="*) ;;
  *) CATALINA_OPTS="$CATALINA_OPTS --add-opens=java.base/java.net=ALL-UNNAMED" ;;
esac
export CATALINA_OPTS
