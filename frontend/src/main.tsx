import { createRoot } from "react-dom/client";
import Farol from "./App";
import WebApp from "./session/WebApp";
import "./styles.css";

const root = document.getElementById("root");
if (root) createRoot(root).render(location.pathname === "/demo" ? <Farol /> : <WebApp />);
