import { useState } from "react";
import { useWorkflowResource } from "./useWorkflowResource";
import { listOf } from "../components/workflowFormat";

export function useReportingPeriod(user, business) {
  const [chosen, setChosen] = useState("");
  const [revision, setRevision] = useState(0);
  const resource = useWorkflowResource(user.role === "MSME_REP" ? "/snapshots/my" : `/snapshots/msme/${business.msmeId}`, user.accessToken, revision);
  const now = new Date();
  const current = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  const months = [...new Set(listOf(resource.data).map(row => String(row.reportingPeriod).slice(0, 7)).filter(month => /^\d{4}-(0[1-9]|1[0-2])$/.test(month) && month < current))].sort().reverse();
  const month = months.includes(chosen) ? chosen : months[0] || "";
  return { resource, months, month, setMonth: setChosen, reload: () => setRevision(value => value + 1) };
}
