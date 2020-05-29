package com.nflscheduling;

public class NflScheduleAlgForAndBack extends NflScheduleAlg {
   //public int fWeekScheduled = 0;
   //public int bWeekScheduled = NflDefs.numberOfWeeks + 1;
   //public int remWeeksToSchedule = bWeekScheduled - fWeekScheduled - 1;
   //public boolean fWeekSuccess = true;
   //public boolean bWeekSuccess = true;
   //public int numWeeksBack = 0;
   //public int weekNum = 0;

   @Override
   public boolean scheduleUnrestrictedGames(final NflSchedule schedule) {

      boolean status = true;

      reschedAttemptsMultiWeeksBack = 0;

      initPromotionInfo(schedule);

      int bWeekLowest = 1000;
      int fWeekHighest = 0;

      sDir = -1;
      fWeekScheduled = 0;
      bWeekScheduled = NflDefs.numberOfWeeks + 1;
      remWeeksToSchedule = bWeekScheduled - fWeekScheduled - 1;

      while(remWeeksToSchedule > 0) {
         if (sDir == -1) {
            weekNum = bWeekScheduled - 1;
         }
         else if (sDir == 1) {
            weekNum = fWeekScheduled + 1;
         }

         if (bWeekScheduled < bWeekLowest) bWeekLowest = bWeekScheduled;
         if (fWeekScheduled > fWeekHighest) fWeekHighest = fWeekScheduled;
         
         initPromotionInfo(schedule);

         logWeeklyDataWeekStart(schedule);

         if (scheduleUnrestrictedWeek(schedule, weekNum)) {
            if (sDir == -1) {
               bWeekSuccess = true;
               bWeekScheduled = weekNum;
            }
            else if (sDir == 1) {
               fWeekSuccess = true;
               fWeekScheduled = weekNum;
            }
            remWeeksToSchedule = bWeekScheduled - fWeekScheduled - 1;

            logWeeklyDataWeekScheduleAttempt(schedule, NflWeeklyData.weekScheduleResultType.success);

            sDir *= -1;  // reverse the scheduling direction
            reschedAttemptsSameWeek = 0;
            numWeeksBack = 0;
         }
         else {
            // failed to schedule weekNum for this direction

            if (sDir == -1) {
               bWeekSuccess = false;
            }
            else if (sDir == 1) {
               fWeekSuccess = false;
            }

            if (reschedAttemptsMultiWeeksBack >= NflDefs.reschedAttemptsMultiWeeksBackLimit) {
               // Exhaustion of retries
               terminationReason = "Failed to schedule all unrestricted games in week: " + weekNum + ", low Week: " + lowestWeekNum;
               // TBD - need something other than lowestWeekNum to show progress - maybe 
               status = false;

               logWeeklyDataWeekScheduleAttempt(schedule, NflWeeklyData.weekScheduleResultType.failExhaustAllRetries);

               break;
            }
            else if (reschedAttemptsSameWeek >= NflDefs.reschedAttemptsSameWeekLimit) {
               // if double fail, increase numWeeksBack
               // unschedule numWeeksBack for both directions
               // reset fWeekScheduled,bWeekScheduled, remWeeksToSchedule for next attempt

               if (!fWeekSuccess && !bWeekSuccess) {
                  numWeeksBack++;
                  if (numWeeksBack > 3) {
                     numWeeksBack = 1;
                  }

                  // unschedule both directions back by numWeeksBack
                  boolean shouldClearHistory = true;
                  int newfWeekScheduled = Math.max(fWeekScheduled-numWeeksBack,0);
                  for (int wn = newfWeekScheduled+1; wn <= fWeekScheduled+1; wn++) {
                     if (!unscheduleUnrestrictedWeek(schedule, wn, shouldClearHistory)) {
                        return false;
                     }
                  }
                  fWeekScheduled = newfWeekScheduled;
                  updateDemotionInfo(schedule, fWeekScheduled+1); 

                  int newbWeekScheduled = Math.min(bWeekScheduled+numWeeksBack,NflDefs.numberOfWeeks + 1);
                  for (int wn = bWeekScheduled-1; wn <= newbWeekScheduled-1; wn++) {
                     if (!unscheduleUnrestrictedWeek(schedule, wn, shouldClearHistory)) {
                        return false;
                     }
                  }
                  bWeekScheduled = newbWeekScheduled;
                  updateDemotionInfo(schedule, bWeekScheduled-1); 
                  reschedAttemptsMultiWeeksBack++;
                  reschedAttemptsSameWeek = 0;

                  remWeeksToSchedule = bWeekScheduled - fWeekScheduled - 1;

                  logWeeklyDataWeekScheduleAttempt(schedule, NflWeeklyData.weekScheduleResultType.failMultiWeeksBack);

                  sDir *= -1;  // reverse the scheduling direction
                  bWeekSuccess = true;
                  fWeekSuccess = true;
               }
               else if (!fWeekSuccess || !bWeekSuccess) {
                  // just one direction has currently failed and exhausted retries
                  // switch back to the other direction and try to make progress there
                  // No change to the scheduled week state

                  boolean shouldClearHistory = true;
                  unscheduleUnrestrictedWeek(schedule, weekNum, shouldClearHistory);
                  updateDemotionInfo(schedule, weekNum); 

                  logWeeklyDataWeekScheduleAttempt(schedule, NflWeeklyData.weekScheduleResultType.failChangeDir);

                  reschedAttemptsSameWeek=0;

                  sDir *= -1;  // reverse the scheduling direction   
               }
            }
            else {

               boolean shouldClearHistory = true;
               unscheduleUnrestrictedWeek(schedule, weekNum, shouldClearHistory);
               updateDemotionInfo(schedule, weekNum); 

               logWeeklyDataWeekScheduleAttempt(schedule, NflWeeklyData.weekScheduleResultType.failRepeatSameWeek);
                           
               reschedAttemptsSameWeek++;
            }
         }

         // write it out here
         writeWeeklyDataToFile();

         remWeeksToSchedule = bWeekScheduled - fWeekScheduled - 1;
      }

      return status;
   }

   public boolean logWeeklyDataWeekStart(NflSchedule schedule) {
      if (scheduler.reschedLogOn) {
         priorWeeklyData = weeklyData;
         weeklyData = new NflWeeklyData();
         weeklyData.init(schedule.unscheduledGames.get(1));
         weeklyData.weekNum = weekNum;
         weeklyData.schedulingDirection = sDir;
         if (priorWeeklyData != null) {
            weeklyData.priorSuccess = priorWeeklyData.success;
            weeklyData.priorNumWeeksBack = priorWeeklyData.numWeeksBack;
            weeklyData.priorWeekResult = priorWeeklyData.weekResult;
         }
      }
      return true;
   }

   public boolean logWeeklyDataWeekScheduleAttempt(NflSchedule schedule,
                                                   NflWeeklyData.weekScheduleResultType weekScheduleResult) {
      if (scheduler.reschedLogOn) {
         if (weekScheduleResult == NflWeeklyData.weekScheduleResultType.success) {
            weeklyData.success = true;
         } else {
            weeklyData.success = false;
         }
         weeklyData.weekResult = weekScheduleResult;
         weeklyData.backwardWeekScheduled = bWeekScheduled;
         weeklyData.forwardWeekScheduled = fWeekScheduled;
         weeklyData.numWeeksBack = 0;
         if (weekScheduleResult == NflWeeklyData.weekScheduleResultType.failMultiWeeksBack) {
            weeklyData.numWeeksBack = numWeeksBack;
         }
         weeklyData.scheduledByes = byesScheduledThisWeek;
         weeklyData.scheduledGames = NflDefs.numberOfTeams / 2 - unscheduledTeams.size() / 2
               - byesScheduledThisWeek / 2;
         weeklyData.unscheduledByes = schedule.unscheduledByes.size();
         weeklyData.unscheduledGames = schedule.unscheduledGames.size();
         weeklyData.unscheduledTeams = unscheduledTeams.size();

         weeklyData.alreadyScheduledRejection = alreadyScheduledRejection;
         weeklyData.backToBackMatchRejection = backToBackMatchRejection;
         weeklyData.resourceUnavailRejection = resourceUnavailRejection;
      }
      return true;
   }
}