-- metrics --

ALTER TABLE ACT_RU_METER_LOG
  ADD REPORTER_ nvarchar(255);

-- job prioritization --
  
ALTER TABLE ACT_RU_JOB
  ADD PRIORITY_ int NOT NULL
  DEFAULT 0;
