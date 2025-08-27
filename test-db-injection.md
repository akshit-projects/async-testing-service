# Database Injection Fix Summary

## Problem
Guice cannot inject `Database` (which is `JdbcBackend$JdbcDatabaseDef`, an inner class) directly into repositories.

## Solution
Created a `DatabaseModule` that provides a `Database` instance using `@Provides` annotation.

## Files Modified

### 1. Created `app/ab/async/tester/modules/DatabaseModule.scala`
- Provides Database instance using configuration
- Uses `@Provides` and `@Singleton` annotations
- Handles configuration with fallback defaults

### 2. Updated `conf/application.conf`
- Added DatabaseModule to enabled modules
- Added PostgreSQL configuration under `slick.dbs.default`
- Added Play Slick dependencies to build.sbt

### 3. Updated `build.sbt`
- Added `play-slick` and `play-slick-evolutions` dependencies
- These provide better integration with Play Framework

## Repositories That Will Be Fixed
All repositories that inject `Database` directly:
- FlowRepositoryImpl
- FlowVersionRepositoryImpl  
- ResourceRepositoryImpl
- ExecutionRepositoryImpl

## Next Steps
1. Run `sbt clean compile` to test the fix
2. If successful, run `sbt run` to start the application
3. The Guice injection error should be resolved

## Database Configuration
The application expects PostgreSQL with these default settings:
- Host: localhost:5432
- Database: asynctester
- User: asynctester  
- Password: asynctester

Update `conf/application.conf` if your database settings are different.
