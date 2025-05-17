package ab.async.tester.filters

import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.api.http.EnabledFilters
import play.filters.gzip.GzipFilter

/**
 * Provides HTTP filters to the application.
 * This combines the default enabled filters with our custom CORS filter.
 */
class Filters @Inject() (
  defaultFilters: EnabledFilters,
  corsFilter: CORSFilter,
  gzipFilter: GzipFilter
) extends DefaultHttpFilters((defaultFilters.filters :+ corsFilter :+ gzipFilter):_*)