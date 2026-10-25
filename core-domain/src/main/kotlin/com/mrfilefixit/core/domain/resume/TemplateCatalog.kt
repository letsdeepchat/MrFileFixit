package com.mrfilefixit.core.domain.resume

// The 15 bundled templates' sample content. Kept apart from the models so the catalog reads as data.

/** The sample content and look of the template for [field]. */
internal fun buildTemplate(field: ResumeField): ResumeTemplate = when (field) {
    ResumeField.SOFTWARE -> software()
    ResumeField.CORE_ENGINEERING -> coreEngineering()
    ResumeField.ARTS -> arts()
    ResumeField.MARKETING -> marketing()
    ResumeField.FINANCE -> finance()
    ResumeField.DATA_SCIENCE -> dataScience()
    ResumeField.HEALTHCARE -> healthcare()
    ResumeField.EDUCATION -> education()
    ResumeField.GRAPHIC_DESIGN -> graphicDesign()
    ResumeField.FRESH_GRADUATE -> freshGraduate()
    ResumeField.BUSINESS_ADMIN -> businessAdmin()
    ResumeField.LEGAL -> legal()
    ResumeField.HUMAN_RESOURCES -> humanResources()
    ResumeField.OPERATIONS -> operations()
    ResumeField.ACADEMIC_CV -> academicCv()
}

private fun software() = ResumeTemplate(
    ResumeField.SOFTWARE,
    ResumeFont.SANS,
    NAVY,
    HeaderLayout.LEFT,
    HeadingStyle.RULE,
    photo = false,
    contact("Aarav Mehta", "Senior Software Engineer", "Bengaluru, India", "github.com/aaravmehta"),
    listOf(
        summary(
            "Software engineer with 6 years of experience building scalable Android and backend services. " +
                "Led teams of 4–6, shipped apps used by 2M+ people, and cut cloud costs by 30%.",
        ),
        skills(
            entry("Languages", bullets = listOf("Kotlin", "Java", "Python", "SQL")),
            entry("Frameworks", bullets = listOf("Jetpack Compose", "Spring Boot", "Ktor")),
            entry("Tools", bullets = listOf("Git", "Docker", "Kubernetes", "AWS", "CI/CD")),
        ),
        experience(
            entry(
                "Senior Software Engineer",
                "Acme Technologies",
                "Bengaluru",
                "Apr 2022 – Present",
                "Led the rewrite of the payments app in Jetpack Compose, raising the crash-free rate to 99.8%",
                "Designed an offline-first sync layer that reduced data usage by 40%",
                "Mentored 5 engineers and introduced code review and testing guidelines",
            ),
            entry(
                "Software Engineer",
                "Bright Apps Pvt Ltd",
                "Pune",
                "Jul 2019 – Mar 2022",
                "Built REST APIs in Spring Boot serving 15K requests per minute",
                "Automated releases with GitHub Actions, cutting release time from 2 days to 2 hours",
            ),
        ),
        projects(
            entry(
                "Expense Tracker",
                "Kotlin, Room, Compose",
                dates = "2023",
                bullets = listOf("Open-source app with 50K downloads and a 4.7 rating"),
            ),
        ),
        educationSection(
            entry("B.Tech, Computer Science", "National Institute of Technology", dates = "2015 – 2019"),
        ),
        certifications(entry("AWS Certified Developer – Associate", "Amazon Web Services", dates = "2023")),
    ),
)

private fun coreEngineering() = ResumeTemplate(
    ResumeField.CORE_ENGINEERING,
    ResumeFont.SANS,
    STEEL,
    HeaderLayout.CENTERED,
    HeadingStyle.RULE,
    photo = false,
    contact("Rohan Verma", "Mechanical Design Engineer", "Chennai, India", "linkedin.com/in/rohanverma"),
    listOf(
        summary(
            "Mechanical engineer with 5 years in product design and manufacturing. Skilled in CAD, FEA and " +
                "lean methods; delivered parts that cut production cost by 18%.",
        ),
        experience(
            entry(
                "Design Engineer",
                "Tata Precision Components",
                "Chennai",
                "Jan 2021 – Present",
                "Designed 40+ components in SolidWorks and validated them with ANSYS",
                "Reduced assembly time by 22% through design-for-manufacture changes",
            ),
            entry(
                "Graduate Engineer Trainee",
                "L&T Heavy Engineering",
                "Vadodara",
                "Jul 2019 – Dec 2020",
                "Supported quality checks and root-cause analysis on the shop floor",
            ),
        ),
        skills(
            entry("Design", bullets = listOf("SolidWorks", "AutoCAD", "CATIA", "GD&T")),
            entry("Analysis", bullets = listOf("ANSYS", "MATLAB", "Six Sigma Green Belt")),
        ),
        projects(
            entry(
                "Low-cost Solar Dryer",
                "Final-year project",
                dates = "2019",
                bullets = listOf("Won state award"),
            ),
        ),
        educationSection(entry("B.E., Mechanical Engineering", "Anna University", dates = "2015 – 2019")),
        certifications(entry("Certified SolidWorks Professional", "Dassault Systèmes", dates = "2022")),
    ),
)

private fun arts() = ResumeTemplate(
    ResumeField.ARTS,
    ResumeFont.SERIF,
    PLUM,
    HeaderLayout.CENTERED,
    HeadingStyle.CAPS,
    photo = true,
    contact("Ananya Iyer", "Visual Artist and Art Educator", "Mumbai, India", "ananyaiyer.art"),
    listOf(
        summary(
            "Visual artist working in painting and mixed media, with 8 years of exhibitions and community " +
                "workshops. Passionate about making art accessible to young people.",
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Exhibitions",
            entries = listOf(
                entry("Colours of the Coast (solo)", "Jehangir Art Gallery", "Mumbai", "2024"),
                entry("New Voices (group)", "India Art Fair", "New Delhi", "2022"),
            ),
        ),
        experience(
            entry(
                "Resident Artist",
                "Kala Foundation",
                "Mumbai",
                "2021 – Present",
                "Ran 60+ workshops for 1,200 students from underserved schools",
            ),
        ),
        educationSection(entry("Master of Fine Arts, Painting", "Sir J.J. School of Art", dates = "2014 – 2016")),
        skills(entry("Media", bullets = listOf("Oil", "Acrylic", "Watercolour", "Mixed media", "Curation"))),
        SampleSection(
            SectionKind.CUSTOM,
            "Awards",
            entries = listOf(entry("Young Artist Award", "Lalit Kala Akademi", dates = "2020")),
        ),
    ),
)

private fun marketing() = ResumeTemplate(
    ResumeField.MARKETING,
    ResumeFont.SANS,
    CORAL,
    HeaderLayout.BAND,
    HeadingStyle.RULE,
    photo = false,
    contact("Kavya Reddy", "Digital Marketing Manager", "Hyderabad, India", "linkedin.com/in/kavyareddy"),
    listOf(
        summary(
            "Data-driven marketer with 7 years in performance and brand marketing. Grew organic traffic 3× " +
                "and managed ₹5 Cr annual ad budgets with a 4.2× return on ad spend.",
        ),
        experience(
            entry(
                "Digital Marketing Manager",
                "ShopKart",
                "Hyderabad",
                "Feb 2021 – Present",
                "Grew monthly active users from 300K to 1.1M through SEO and paid campaigns",
                "Built a 10-person team across content, social and performance marketing",
            ),
            entry(
                "Marketing Executive",
                "Zenith Brands",
                "Bengaluru",
                "Jun 2017 – Jan 2021",
                "Launched 12 product campaigns; best campaign lifted sales by 35%",
            ),
        ),
        skills(
            entry("Channels", bullets = listOf("SEO", "Google Ads", "Meta Ads", "Email", "Influencer")),
            entry("Tools", bullets = listOf("Google Analytics 4", "HubSpot", "SEMrush", "Canva")),
        ),
        educationSection(
            entry("MBA, Marketing", "Symbiosis Institute of Business Management", dates = "2015 – 2017"),
        ),
        certifications(entry("Google Ads Search Certification", "Google", dates = "2024")),
    ),
)

private fun finance() = ResumeTemplate(
    ResumeField.FINANCE,
    ResumeFont.SERIF,
    FOREST,
    HeaderLayout.LEFT,
    HeadingStyle.RULE,
    photo = false,
    contact("Vikram Nair", "Chartered Accountant | Financial Analyst", "Mumbai, India", ""),
    listOf(
        summary(
            "Chartered Accountant with 6 years in financial planning, audit and analysis. Built forecasting " +
                "models that improved budget accuracy to within 3%.",
        ),
        experience(
            entry(
                "Senior Financial Analyst",
                "Unity Capital",
                "Mumbai",
                "Mar 2021 – Present",
                "Own monthly close, variance analysis and board reporting for a ₹900 Cr portfolio",
                "Automated MIS reports in Power BI, saving 25 hours a month",
            ),
            entry(
                "Audit Associate",
                "Deloitte",
                "Mumbai",
                "Aug 2018 – Feb 2021",
                "Led statutory audits for 8 clients in manufacturing and retail",
            ),
        ),
        educationSection(
            entry("Chartered Accountant", "The Institute of Chartered Accountants of India", dates = "2018"),
            entry("B.Com", "University of Mumbai", dates = "2012 – 2015"),
        ),
        skills(
            entry("Finance", bullets = listOf("FP&A", "IFRS", "Ind AS", "Taxation", "Valuation")),
            entry("Tools", bullets = listOf("Excel (advanced)", "SAP FICO", "Tally", "Power BI")),
        ),
        certifications(entry("CFA Level II Candidate", "CFA Institute", dates = "2025")),
    ),
)

private fun dataScience() = ResumeTemplate(
    ResumeField.DATA_SCIENCE,
    ResumeFont.SANS,
    TEAL,
    HeaderLayout.LEFT,
    HeadingStyle.BAND,
    photo = false,
    contact("Ishaan Kapoor", "Data Scientist | Machine Learning Engineer", "Gurugram, India", "github.com/ishaank"),
    listOf(
        summary(
            "Data scientist with 5 years of experience in machine learning, NLP and experimentation. Shipped " +
                "models to production that lifted conversion by 12% and cut fraud losses by ₹4 Cr a year.",
        ),
        skills(
            entry("ML and AI", bullets = listOf("PyTorch", "TensorFlow", "scikit-learn", "LLMs", "NLP")),
            entry("Data", bullets = listOf("Python", "SQL", "Spark", "Airflow", "dbt")),
            entry("MLOps", bullets = listOf("MLflow", "Docker", "AWS SageMaker", "A/B testing")),
        ),
        experience(
            entry(
                "Data Scientist",
                "FinEdge",
                "Gurugram",
                "May 2022 – Present",
                "Built a gradient-boosted fraud model (AUC 0.94) scoring 2M transactions a day",
                "Fine-tuned a language model to triage support tickets, cutting handling time by 30%",
            ),
            entry(
                "Data Analyst",
                "Insight Labs",
                "Noida",
                "Jul 2020 – Apr 2022",
                "Designed 40+ A/B tests and the company's experimentation guidelines",
            ),
        ),
        projects(
            entry(
                "Crop Yield Prediction",
                "Python, XGBoost",
                dates = "2021",
                bullets = listOf("Kaggle silver medal; top 3% of 2,400 teams"),
            ),
        ),
        educationSection(entry("M.Tech, Artificial Intelligence", "IIIT Hyderabad", dates = "2018 – 2020")),
        certifications(entry("TensorFlow Developer Certificate", "Google", dates = "2023")),
    ),
)

private fun healthcare() = ResumeTemplate(
    ResumeField.HEALTHCARE,
    ResumeFont.SANS,
    MEDICAL_BLUE,
    HeaderLayout.CENTERED,
    HeadingStyle.RULE,
    photo = true,
    contact("Dr. Meera Joshi", "Registered Nurse | Critical Care", "Pune, India", ""),
    listOf(
        summary(
            "Registered nurse with 6 years in intensive care. Calm under pressure, trained in advanced life " +
                "support, and committed to safe, compassionate patient care.",
        ),
        SampleSection(
            SectionKind.CERTIFICATIONS,
            "Licences and Certifications",
            entries = listOf(
                entry("Registered Nurse and Midwife", "Maharashtra Nursing Council", dates = "2018"),
                entry("Advanced Cardiac Life Support (ACLS)", "American Heart Association", dates = "2024"),
            ),
        ),
        SampleSection(
            SectionKind.EXPERIENCE,
            "Clinical Experience",
            entries = listOf(
                entry(
                    "Staff Nurse, ICU",
                    "Ruby Hall Clinic",
                    "Pune",
                    "Jan 2020 – Present",
                    "Care for 3–4 critical patients per shift in a 24-bed ICU",
                    "Trained 15 new nurses on ventilator and infection-control protocols",
                ),
                entry("Staff Nurse", "Sahyadri Hospitals", "Pune", "Jul 2018 – Dec 2019"),
            ),
        ),
        educationSection(entry("B.Sc. Nursing", "Bharati Vidyapeeth College of Nursing", dates = "2014 – 2018")),
        skills(
            entry("Clinical", bullets = listOf("Critical care", "Ventilator management", "IV therapy", "Triage")),
            entry("Languages", bullets = listOf("English", "Hindi", "Marathi")),
        ),
    ),
)

private fun education() = ResumeTemplate(
    ResumeField.EDUCATION,
    ResumeFont.SERIF,
    BURGUNDY,
    HeaderLayout.CENTERED,
    HeadingStyle.RULE,
    photo = false,
    contact("Sneha Pillai", "Secondary School Teacher | Mathematics", "Kochi, India", ""),
    listOf(
        summary(
            "Mathematics teacher with 9 years at CBSE schools. Raised board results to a 96% distinction rate " +
                "using activity-based learning and regular formative assessment.",
        ),
        SampleSection(
            SectionKind.EXPERIENCE,
            "Teaching Experience",
            entries = listOf(
                entry(
                    "Senior Mathematics Teacher (TGT/PGT)",
                    "Delhi Public School",
                    "Kochi",
                    "2019 – Present",
                    "Teach classes 9–12; class average in boards rose from 72% to 86%",
                    "Coordinate the maths olympiad club; 14 students reached the national round",
                ),
                entry("Mathematics Teacher", "St. Mary's School", "Kochi", "2016 – 2019"),
            ),
        ),
        educationSection(
            entry("B.Ed.", "University of Kerala", dates = "2015 – 2016"),
            entry("M.Sc., Mathematics", "Cochin University of Science and Technology", dates = "2013 – 2015"),
        ),
        certifications(entry("CTET (Paper II) qualified", "CBSE", dates = "2016")),
        skills(entry("Teaching", bullets = listOf("Lesson planning", "Smart-class tools", "Google Classroom"))),
    ),
)

private fun graphicDesign() = ResumeTemplate(
    ResumeField.GRAPHIC_DESIGN,
    ResumeFont.SANS,
    VIOLET,
    HeaderLayout.BAND,
    HeadingStyle.CAPS,
    photo = true,
    contact("Zoya Khan", "Graphic and Brand Designer", "New Delhi, India", "behance.net/zoyakhan"),
    listOf(
        summary(
            "Brand designer with 5 years creating identities, packaging and social campaigns for 30+ " +
                "clients. Strong in typography, layout and design systems.",
        ),
        skills(
            entry("Design", bullets = listOf("Branding", "Typography", "Packaging", "UI design", "Motion")),
            entry(
                "Tools",
                bullets = listOf("Adobe Illustrator", "Photoshop", "InDesign", "Figma", "After Effects"),
            ),
        ),
        experience(
            entry(
                "Senior Graphic Designer",
                "Pixel Studio",
                "New Delhi",
                "Mar 2022 – Present",
                "Led rebrands for 8 consumer brands, including a D2C label that grew sales 2×",
                "Built a shared design system that halved turnaround time",
            ),
            entry("Graphic Designer", "AdWorks", "Gurugram", "Jun 2019 – Feb 2022"),
        ),
        SampleSection(
            SectionKind.PROJECTS,
            "Selected Work",
            entries = listOf(entry("Chai Co. rebrand", "Identity and packaging", dates = "2024")),
        ),
        educationSection(
            entry("B.Des., Communication Design", "National Institute of Design", dates = "2015 – 2019"),
        ),
    ),
)

private fun freshGraduate() = ResumeTemplate(
    ResumeField.FRESH_GRADUATE,
    ResumeFont.SANS,
    ROYAL,
    HeaderLayout.CENTERED,
    HeadingStyle.RULE,
    photo = false,
    contact(
        "Rahul Singh",
        "B.Tech Graduate | Electronics and Communication",
        "Lucknow, India",
        "linkedin.com/in/rahul",
    ),
    listOf(
        SampleSection(
            SectionKind.SUMMARY,
            "Career Objective",
            text = "Motivated electronics graduate looking for an entry-level role in embedded systems, where I " +
                "can apply my project experience and learn from an experienced team.",
        ),
        educationSection(
            entry(
                "B.Tech, Electronics and Communication",
                "Dr. A.P.J. Abdul Kalam Technical University",
                dates = "2021 – 2025",
                bullets = listOf("CGPA 8.4 / 10"),
            ),
            entry("Class XII (CBSE)", "Kendriya Vidyalaya", dates = "2021", bullets = listOf("91%")),
        ),
        SampleSection(
            SectionKind.PROJECTS,
            "Academic Projects",
            entries = listOf(
                entry(
                    "Smart Irrigation System",
                    "Arduino, IoT sensors",
                    dates = "2024",
                    bullets = listOf("Cut water use by 35% in a campus garden trial"),
                ),
            ),
        ),
        SampleSection(
            SectionKind.EXPERIENCE,
            "Internships",
            entries = listOf(
                entry(
                    "Embedded Systems Intern",
                    "Bharat Electronics Limited",
                    "Ghaziabad",
                    "Jun – Jul 2024",
                    "Tested firmware for a sensor module and documented 20 test cases",
                ),
            ),
        ),
        skills(
            entry("Technical", bullets = listOf("C", "Embedded C", "Python", "Arduino", "MATLAB")),
            entry("Soft skills", bullets = listOf("Teamwork", "Communication", "Problem solving")),
        ),
        certifications(entry("NPTEL: Embedded Systems Design (Elite)", "IIT Kharagpur", dates = "2024")),
        SampleSection(
            SectionKind.CUSTOM,
            "Achievements and Activities",
            entries = listOf(entry("Runner-up, Smart India Hackathon", dates = "2023")),
        ),
    ),
)

private fun businessAdmin() = ResumeTemplate(
    ResumeField.BUSINESS_ADMIN,
    ResumeFont.SERIF,
    NAVY,
    HeaderLayout.LEFT,
    HeadingStyle.RULE,
    photo = false,
    contact("Arjun Malhotra", "Business Operations Manager", "Delhi NCR, India", "linkedin.com/in/arjunm"),
    listOf(
        summary(
            "MBA with 8 years in business operations and strategy. Led cross-functional programmes that " +
                "added ₹40 Cr in revenue and improved margins by 6 points.",
        ),
        experience(
            entry(
                "Operations Manager",
                "Metro Retail Group",
                "Gurugram",
                "Apr 2020 – Present",
                "Run operations for 45 stores and a team of 300",
                "Introduced weekly KPI reviews that cut stock-outs by 28%",
            ),
            entry("Business Analyst", "KPMG", "Gurugram", "Jul 2016 – Mar 2020"),
        ),
        educationSection(entry("MBA, Strategy and Operations", "IIM Lucknow", dates = "2014 – 2016")),
        skills(
            entry(
                "Management",
                bullets = listOf("Strategy", "P&L ownership", "Stakeholder management", "Process improvement"),
            ),
            entry("Tools", bullets = listOf("Excel", "Power BI", "SAP", "Jira")),
        ),
        certifications(entry("PMP", "Project Management Institute", dates = "2022")),
    ),
)

private fun legal() = ResumeTemplate(
    ResumeField.LEGAL,
    ResumeFont.SERIF,
    CHARCOAL,
    HeaderLayout.CENTERED,
    HeadingStyle.CAPS,
    photo = false,
    contact("Aditi Rao", "Corporate Lawyer", "Bengaluru, India", ""),
    listOf(
        summary(
            "Corporate lawyer with 6 years advising on M&A, contracts and regulatory compliance. Advised on " +
                "transactions worth over ₹2,000 Cr.",
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Bar Admission",
            entries = listOf(entry("Advocate, Bar Council of Karnataka", dates = "2019")),
        ),
        SampleSection(
            SectionKind.EXPERIENCE,
            "Legal Experience",
            entries = listOf(
                entry(
                    "Senior Associate",
                    "Khaitan & Co",
                    "Bengaluru",
                    "2022 – Present",
                    "Led due diligence and drafting on 12 M&A deals",
                    "Advise start-ups on shareholder agreements and FEMA compliance",
                ),
                entry("Associate", "Trilegal", "Mumbai", "2019 – 2022"),
            ),
        ),
        educationSection(
            entry("B.A. LL.B. (Hons.)", "National Law School of India University", dates = "2014 – 2019"),
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Publications",
            entries = listOf(
                entry("\"Insolvency and Start-ups\", Indian Journal of Law and Technology", dates = "2021"),
            ),
        ),
        skills(entry("Practice areas", bullets = listOf("M&A", "Contracts", "Corporate governance", "Compliance"))),
    ),
)

private fun humanResources() = ResumeTemplate(
    ResumeField.HUMAN_RESOURCES,
    ResumeFont.SANS,
    ROSE,
    HeaderLayout.LEFT,
    HeadingStyle.BAND,
    photo = false,
    contact("Neha Gupta", "HR Business Partner", "Noida, India", "linkedin.com/in/nehagupta"),
    listOf(
        summary(
            "HR professional with 7 years in talent acquisition, employee engagement and HR operations. " +
                "Hired 400+ people and cut attrition from 24% to 14%.",
        ),
        experience(
            entry(
                "HR Business Partner",
                "TechNova Solutions",
                "Noida",
                "Jan 2021 – Present",
                "Partner with leaders of 3 business units (600 employees) on workforce planning",
                "Launched an onboarding programme that raised 90-day retention to 95%",
            ),
            entry("HR Executive", "Spark Services", "Delhi", "Jun 2017 – Dec 2020"),
        ),
        skills(
            entry("HR", bullets = listOf("Recruitment", "Payroll", "Labour law", "Performance management")),
            entry("Tools", bullets = listOf("Workday", "SAP SuccessFactors", "Naukri RMS", "Excel")),
        ),
        educationSection(entry("MBA, Human Resource Management", "XLRI Jamshedpur", dates = "2015 – 2017")),
        certifications(entry("SHRM Certified Professional (SHRM-CP)", "SHRM", dates = "2023")),
    ),
)

private fun operations() = ResumeTemplate(
    ResumeField.OPERATIONS,
    ResumeFont.CONDENSED,
    AMBER_BROWN,
    HeaderLayout.LEFT,
    HeadingStyle.RULE,
    photo = false,
    contact("Sandeep Yadav", "Supply Chain and Logistics Manager", "Ahmedabad, India", ""),
    listOf(
        summary(
            "Supply chain manager with 9 years in procurement, warehousing and logistics. Cut freight costs " +
                "by 17% and improved on-time delivery to 98%.",
        ),
        experience(
            entry(
                "Supply Chain Manager",
                "Adani Logistics",
                "Ahmedabad",
                "Feb 2020 – Present",
                "Manage 6 warehouses (500K sq ft) and a fleet of 120 vehicles",
                "Introduced a WMS that improved picking accuracy to 99.6%",
            ),
            entry("Procurement Executive", "Arvind Ltd", "Ahmedabad", "Jul 2015 – Jan 2020"),
        ),
        skills(
            entry("Operations", bullets = listOf("Procurement", "Inventory planning", "Lean", "Vendor management")),
            entry("Tools", bullets = listOf("SAP MM", "Oracle WMS", "Excel", "Power BI")),
        ),
        educationSection(entry("PGDM, Supply Chain Management", "NITIE Mumbai", dates = "2013 – 2015")),
        certifications(entry("APICS Certified Supply Chain Professional (CSCP)", "ASCM", dates = "2021")),
    ),
)

private fun academicCv() = ResumeTemplate(
    ResumeField.ACADEMIC_CV,
    ResumeFont.SERIF,
    NAVY,
    HeaderLayout.CENTERED,
    HeadingStyle.RULE,
    photo = true,
    contact(
        "Dr. Karthik Subramanian",
        "Assistant Professor, Department of Physics",
        "Chennai, India",
        "orcid.org/0000-0000",
    ),
    listOf(
        SampleSection(
            SectionKind.SUMMARY,
            "Research Interests",
            text = "Condensed matter physics, two-dimensional materials and quantum transport.",
        ),
        educationSection(
            entry("Ph.D., Physics", "Indian Institute of Science", "Bengaluru", "2014 – 2019"),
            entry("M.Sc., Physics", "University of Madras", dates = "2012 – 2014"),
        ),
        SampleSection(
            SectionKind.EXPERIENCE,
            "Academic Appointments",
            entries = listOf(
                entry("Assistant Professor", "IIT Madras", "Chennai", "2022 – Present"),
                entry("Postdoctoral Fellow", "University of Cambridge", "Cambridge, UK", "2019 – 2022"),
            ),
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Publications",
            entries = listOf(
                entry(
                    "Subramanian K., et al. \"Electron transport in twisted bilayer graphene.\"",
                    "Physical Review B 105, 115402",
                    dates = "2022",
                ),
            ),
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Grants and Awards",
            entries = listOf(entry("SERB Start-up Research Grant (₹30 lakh)", dates = "2023")),
        ),
        SampleSection(
            SectionKind.CUSTOM,
            "Teaching",
            entries = listOf(
                entry("PH1010 Quantum Mechanics (undergraduate)", "IIT Madras", dates = "2022 – Present"),
            ),
        ),
        skills(entry("Techniques", bullets = listOf("Cryogenic transport", "Lithography", "Python", "COMSOL"))),
    ),
)

private fun contact(name: String, headline: String, location: String, link: String) = ContactInfo(
    fullName = name,
    headline = headline,
    email = name.lowercase().replace("dr. ", "").replace(' ', '.') + "@example.com",
    phone = "+91 98765 43210",
    location = location,
    links = listOfNotNull(link.takeIf { it.isNotBlank() }),
)

private fun summary(text: String) = SampleSection(SectionKind.SUMMARY, "Professional Summary", text = text)

private fun experience(vararg entries: ResumeEntry) =
    SampleSection(SectionKind.EXPERIENCE, "Work Experience", entries = entries.toList())

private fun educationSection(vararg entries: ResumeEntry) =
    SampleSection(SectionKind.EDUCATION, "Education", entries = entries.toList())

private fun skills(vararg entries: ResumeEntry) =
    SampleSection(SectionKind.SKILLS, "Skills", entries = entries.toList())

private fun projects(vararg entries: ResumeEntry) =
    SampleSection(SectionKind.PROJECTS, "Projects", entries = entries.toList())

private fun certifications(vararg entries: ResumeEntry) =
    SampleSection(SectionKind.CERTIFICATIONS, "Certifications", entries = entries.toList())

/** Sample entries get their ids when a resume is created from the template. */
private fun entry(
    title: String,
    subtitle: String = "",
    location: String = "",
    dates: String = "",
    vararg points: String,
    bullets: List<String> = points.toList(),
) = ResumeEntry(id = "", title = title, subtitle = subtitle, location = location, dates = dates, bullets = bullets)

// Muted accents that stay readable when printed in greyscale.
private const val NAVY = -0xdcaa6b // 0xFF235595
private const val STEEL = -0xbfa98b // 0xFF405675
private const val PLUM = -0x9fcd94 // 0xFF60326C
private const val CORAL = -0x3fb3c4 // 0xFFC04C3C
private const val FOREST = -0xe192c7 // 0xFF1E6D39
private const val TEAL = -0xff8a8d // 0xFF007573
private const val MEDICAL_BLUE = -0xff9a4c // 0xFF0065B4
private const val BURGUNDY = -0x7fdcd2 // 0xFF80232E
private const val VIOLET = -0xa6c74b // 0xFF5938B5
private const val ROYAL = -0xd2b23e // 0xFF2D4DC2
private const val CHARCOAL = -0xcccccd // 0xFF333333
private const val ROSE = -0x50d389 // 0xFFAF2C77
private const val AMBER_BROWN = -0x7bb4f2 // 0xFF844B0E
