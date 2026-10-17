import os
import uvicorn
from dotenv import load_dotenv
from . import ROOT


if __name__ == "__main__":
    load_dotenv(ROOT / ".env", override=False)
    # Freeze the listening port so shared settings loaded during startup cannot
    # make Eureka advertise a different port from the one bound by Uvicorn.
    port = int(os.getenv("DASHBOARD_PORT", "8086"))
    os.environ["DASHBOARD_PORT"] = str(port)
    uvicorn.run("dashboard.api:app", host="127.0.0.1",
                port=port, workers=1)
