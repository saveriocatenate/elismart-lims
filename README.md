# EliSmart 🧪🔬
### AI-Powered Biotech Dose-Response Analyzer

![EliSmart Logo](assets/EliSmartLogo.png)

**EliSmart** is a specialized Laboratory Information Management System (LIMS) designed to streamline the analysis of **ELISA dose-response assays**. Built for scientists and biotechnologists, it transforms raw laboratory data into validated, high-quality insights using a robust Java backend and an intuitive Streamlit frontend.

---

## 🌟 Key Features

* **Protocol-Based Management:** Define reusable assay templates with specific curve-fitting types (4PL/5PL), required reagents, and acceptance criteria.
* **Replicate Analysis:** Automatically handles **Measurement Pairs** (duplicates), calculating Mean Signal, %CV (Precision), and %Recovery (Accuracy).
* **Full Traceability:** Link every experiment to specific reagent **Lot Numbers** and expiration dates to track systemic issues or degradation.
* **Smart Validation:** Instant OK/KO status based on protocol-defined thresholds for control points and calibration curves.
* **AI Insights:** Integrated with Google Gemini to provide qualitative feedback on curve anomalies, pipetting errors, and cross-experiment comparisons.
* **Search & Compare:** Powerful filtering by date, name, or status, with the ability to compare two experiments side-by-side.

---

## 🏗️ Tech Stack

* **Backend:** Java 17+ with Spring Boot
* **Frontend:** Python with Streamlit
* **Database:** H2 (Local/File-based for easy lab deployment)
* **Intelligence:** Google Gemini API Integration
* **Access:** Pinggy (Remote access tunneling)

---

## 📊 Data Model Logic

The system follows a strict hierarchical structure to ensure data integrity:
1.  **Protocol:** The "Template" defining the rules.
2.  **Experiment:** The "Instance" representing a day of work.
3.  **MeasurementPair:** The core data unit containing duplicate signals and calculated metrics.
4.  **Reagent Catalog:** Master data for all laboratory components.

---

## 🚀 Getting Started

*(Note: This project is currently in the functional testing phase)*

1.  **Clone the repo:** `git clone https://github.com/tuo-username/elismart-lims.git`
2.  **Build the Backend:** `./mvnw clean install`
3.  **Run Streamlit:** `streamlit run frontend/main.py`

---

## 🛡️ License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Developed with ❤️ for the Biology & Biotech community.*