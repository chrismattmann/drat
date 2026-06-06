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

  case " $CATALINA_OPTS " in
    *" -Dsolr.solr.home="*) ;;
    *) CATALINA_OPTS="$CATALINA_OPTS -Dsolr.solr.home=$DRAT_HOME/solr" ;;
  esac
  export DRAT_HOME CATALINA_OPTS
fi
