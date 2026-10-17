"""Start the HTTP server with matching advertised and listening ports."""
import os
from dotenv import load_dotenv
from .config import ROOT


def main():
    import uvicorn
    load_dotenv(ROOT / ".env", override=False)
    uvicorn.run("recommendation.api:app", host=os.getenv("API_BIND_HOST", "127.0.0.1"),
                port=int(os.getenv("SERVICE_PORT", "8085")), workers=1,
                timeout_keep_alive=5)


if __name__ == "__main__":
    main()
